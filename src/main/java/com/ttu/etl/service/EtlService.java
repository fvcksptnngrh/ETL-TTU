package com.ttu.etl.service;

import com.ttu.etl.dto.ErrorLogDto;
import com.ttu.etl.dto.ExtractedReceipt;
import com.ttu.etl.dto.JobResponse;
import com.ttu.etl.dto.UploadResponse;
import com.ttu.etl.dto.ValidatedReceipt;
import com.ttu.etl.entity.ErrorLog;
import com.ttu.etl.entity.EtlJob;
import com.ttu.etl.exception.EtlException;
import com.ttu.etl.exception.JobNotFoundException;
import com.ttu.etl.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class EtlService {

    private final EtlJobRepository etlJobRepository;
    private final TransactionHeaderRepository transactionHeaderRepository;
    private final ProductRepository productRepository;
    private final ErrorLogRepository errorLogRepository;
    private final ExcelExtractorService extractorService;
    private final DataTransformerService transformerService;
    private final DataLoaderService loaderService;
    private final EtlService self;

    @Value("${etl.upload-dir:uploads}")
    private String uploadDir;

    public EtlService(EtlJobRepository etlJobRepository,
                      TransactionHeaderRepository transactionHeaderRepository,
                      ProductRepository productRepository,
                      ErrorLogRepository errorLogRepository,
                      ExcelExtractorService extractorService,
                      DataTransformerService transformerService,
                      DataLoaderService loaderService,
                      @Lazy EtlService self) {
        this.etlJobRepository = etlJobRepository;
        this.transactionHeaderRepository = transactionHeaderRepository;
        this.productRepository = productRepository;
        this.errorLogRepository = errorLogRepository;
        this.extractorService = extractorService;
        this.transformerService = transformerService;
        this.loaderService = loaderService;
        this.self = self;
    }

    public UploadResponse uploadAndProcess(MultipartFile file) {
        validateFile(file);

        String jobId = UUID.randomUUID().toString();
        EtlJob etlJob = EtlJob.builder()
                .jobId(jobId)
                .fileName(file.getOriginalFilename())
                .status(EtlJob.JobStatus.PENDING)
                .totalRecords(0)
                .extractedRecords(0)
                .validRecords(0)
                .failedRecords(0)
                .duplicateRecords(0)
                .loadedRecords(0)
                .build();

        etlJob = etlJobRepository.save(etlJob);

        String filePath = saveFile(file, jobId);
        etlJob.setFilePath(filePath);
        etlJob = etlJobRepository.saveAndFlush(etlJob);

        self.processFileAsync(etlJob.getId(), filePath);

        return UploadResponse.builder()
                .jobId(jobId)
                .message("File uploaded successfully. Processing started.")
                .status("PENDING")
                .build();
    }

    @Async
    @Transactional
    public void processFileAsync(Long etlJobId, String filePath) {
        EtlJob etlJob = etlJobRepository.findById(etlJobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found"));

        try {
            log.info("[{}] START - Processing file: {}", etlJob.getJobId(), etlJob.getFileName());
            etlJob.setStartTime(LocalDateTime.now());
            etlJob.setStatus(EtlJob.JobStatus.EXTRACTING);
            etlJobRepository.save(etlJob);

            List<ExtractedReceipt> extractedReceipts = extractorService.extractFromExcel(
                    Paths.get(filePath));

            etlJob.setExtractedRecords(extractedReceipts.size());
            etlJob.setTotalRecords(extractedReceipts.size());
            log.info("[{}] EXTRACT - {} receipts extracted", etlJob.getJobId(), extractedReceipts.size());

            etlJob.setStatus(EtlJob.JobStatus.TRANSFORMING);
            etlJobRepository.save(etlJob);

            List<ErrorLog> errorLogs = new ArrayList<>();
            List<ValidatedReceipt> validatedReceipts = transformerService.transform(
                    extractedReceipts, etlJob, errorLogs);

            long validCount = validatedReceipts.stream().filter(ValidatedReceipt::isValid).count();
            long failedCount = validatedReceipts.size() - validCount;
            etlJob.setValidRecords((int) validCount);
            etlJob.setFailedRecords((int) failedCount);
            log.info("[{}] TRANSFORM - {} valid, {} failed",
                    etlJob.getJobId(), validCount, failedCount);

            etlJob.setStatus(EtlJob.JobStatus.LOADING);
            etlJobRepository.save(etlJob);

            int loadedCount = loaderService.loadData(validatedReceipts, etlJob, errorLogs);
            etlJob.setLoadedRecords(loadedCount);
            log.info("[{}] LOAD - {} receipts loaded to database", etlJob.getJobId(), loadedCount);

            etlJob.setStatus(EtlJob.JobStatus.COMPLETED);
            etlJob.setEndTime(LocalDateTime.now());
            etlJob.setDurationSeconds(ChronoUnit.SECONDS.between(etlJob.getStartTime(), etlJob.getEndTime()));
            etlJobRepository.save(etlJob);

            log.info("[{}] END - Success (Duration: {}s)", etlJob.getJobId(), etlJob.getDurationSeconds());

        } catch (Exception e) {
            log.error("[{}] FAILED - Error: {}", etlJob.getJobId(), e.getMessage(), e);
            etlJob.setStatus(EtlJob.JobStatus.FAILED);
            etlJob.setErrorMessage(e.getMessage());
            etlJob.setEndTime(LocalDateTime.now());
            if (etlJob.getStartTime() != null) {
                etlJob.setDurationSeconds(ChronoUnit.SECONDS.between(etlJob.getStartTime(), etlJob.getEndTime()));
            }
            etlJobRepository.save(etlJob);
        }
    }

    /**
     * List view: all jobs with summary stats.
     */
    @Transactional(readOnly = true)
    public Page<JobResponse> getJobs(Pageable pageable) {
        return etlJobRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(job -> {
                    JobResponse resp = JobResponse.fromEntity(job);
                    resp.setTransactionCount(transactionHeaderRepository.countByEtlJobId(job.getId()));
                    resp.setProductCount(productRepository.countByEtlJobId(job.getId()));
                    resp.setErrorCount(errorLogRepository.countByEtlJobIdAndErrorTypeNot(job.getId(), ErrorLog.ErrorType.QTY_FRACTION_INFO));
                    return resp;
                });
    }

    /**
     * Detail view: single job with all info including errors.
     */
    @Transactional(readOnly = true)
    public JobResponse getJobDetail(String jobId) {
        EtlJob job = etlJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + jobId));

        JobResponse resp = JobResponse.fromEntity(job);
        resp.setTransactionCount(transactionHeaderRepository.countByEtlJobId(job.getId()));
        resp.setProductCount(productRepository.countByEtlJobId(job.getId()));
        resp.setErrorCount(errorLogRepository.countByEtlJobIdAndErrorTypeNot(job.getId(), ErrorLog.ErrorType.QTY_FRACTION_INFO));

        // Include errors inline
        List<ErrorLog> errors = errorLogRepository.findByEtlJobOrderByRowNumberAsc(job);
        resp.setErrors(errors.stream().map(e -> ErrorLogDto.builder()
                .rowNumber(e.getRowNumber())
                .fieldName(e.getFieldName())
                .errorDescription(e.getErrorDescription())
                .originalValue(e.getOriginalValue())
                .errorType(e.getErrorType().name())
                .createdAt(e.getCreatedAt())
                .build()).toList());

        // Validation summary
        resp.setFailedTransactionNos(
                transactionHeaderRepository.findFailedTransactionNosByEtlJobId(job.getId()));
        resp.setQtyFractionIssues(errors.stream()
                .filter(e -> e.getErrorType() == ErrorLog.ErrorType.QTY_FRACTION_INFO)
                .count());

        return resp;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new EtlException("File is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new EtlException("Only .xlsx files are supported");
        }

        long maxSize = 50 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new EtlException("File size exceeds 50MB limit");
        }
    }

    private String saveFile(MultipartFile file, String jobId) {
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String filename = jobId + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath);

            log.info("File saved: {}", filePath.toAbsolutePath());
            return filePath.toAbsolutePath().toString();

        } catch (IOException e) {
            throw new EtlException("Failed to save file: " + e.getMessage(), e);
        }
    }
}
