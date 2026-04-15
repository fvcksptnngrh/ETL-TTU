package com.ttu.etl.service;

import com.ttu.etl.dto.ErrorLogDto;
import com.ttu.etl.dto.JobResponse;
import com.ttu.etl.dto.UploadResponse;
import com.ttu.etl.entity.ErrorLog;
import com.ttu.etl.entity.EtlJob;
import com.ttu.etl.exception.EtlException;
import com.ttu.etl.exception.JobNotFoundException;
import com.ttu.etl.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtlService {

    private final EtlJobRepository etlJobRepository;
    private final TransactionHeaderRepository transactionHeaderRepository;
    private final ProductRepository productRepository;
    private final ErrorLogRepository errorLogRepository;
    private final AsyncEtlProcessor asyncEtlProcessor;

    @Value("${etl.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public UploadResponse uploadAndProcess(MultipartFile file) {
        validateFile(file);

        String jobId = UUID.randomUUID().toString();
        String filePath = saveFile(file, jobId);

        EtlJob etlJob = EtlJob.builder()
                .jobId(jobId)
                .fileName(file.getOriginalFilename())
                .filePath(filePath)
                .status(EtlJob.JobStatus.PENDING)
                .totalRecords(0)
                .extractedRecords(0)
                .validRecords(0)
                .failedRecords(0)
                .duplicateRecords(0)
                .loadedRecords(0)
                .build();

        etlJob = etlJobRepository.saveAndFlush(etlJob);

        asyncEtlProcessor.processFile(etlJob.getId(), filePath);

        return UploadResponse.builder()
                .jobId(jobId)
                .message("File uploaded successfully. Processing started.")
                .status("PENDING")
                .build();
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

        List<ErrorLog> errors = errorLogRepository.findByEtlJobOrderByRowNumberAsc(job);
        resp.setErrors(errors.stream().map(e -> ErrorLogDto.builder()
                .rowNumber(e.getRowNumber())
                .fieldName(e.getFieldName())
                .errorDescription(e.getErrorDescription())
                .originalValue(e.getOriginalValue())
                .errorType(e.getErrorType().name())
                .createdAt(e.getCreatedAt())
                .build()).toList());

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
