package com.ttu.etl.service;

import com.ttu.etl.dto.ExtractedReceipt;
import com.ttu.etl.dto.ValidatedReceipt;
import com.ttu.etl.entity.ErrorLog;
import com.ttu.etl.entity.EtlJob;
import com.ttu.etl.exception.JobNotFoundException;
import com.ttu.etl.repository.EtlJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Transactional boundary for an ETL pipeline run. Kept as a separate bean
 * from {@link AsyncEtlProcessor} so that @Async and @Transactional proxies
 * are applied independently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EtlPipelineRunner {

    private final EtlJobRepository etlJobRepository;
    private final ExcelExtractorService extractorService;
    private final DataTransformerService transformerService;
    private final DataLoaderService loaderService;

    @Transactional
    public void run(Long etlJobId, String filePath) {
        EtlJob etlJob = etlJobRepository.findById(etlJobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found"));

        log.info("[{}] START - Processing file: {}", etlJob.getJobId(), etlJob.getFileName());
        etlJob.setStartTime(LocalDateTime.now());
        etlJob.setStatus(EtlJob.JobStatus.EXTRACTING);
        etlJobRepository.save(etlJob);

        List<ExtractedReceipt> extractedReceipts = extractorService.extractFromExcel(Paths.get(filePath));

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
        log.info("[{}] TRANSFORM - {} valid, {} failed", etlJob.getJobId(), validCount, failedCount);

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
    }

    @Transactional
    public void markFailed(Long etlJobId, Exception e) {
        etlJobRepository.findById(etlJobId).ifPresent(job -> {
            log.error("[{}] FAILED - Error: {}", job.getJobId(), e.getMessage(), e);
            job.setStatus(EtlJob.JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setEndTime(LocalDateTime.now());
            if (job.getStartTime() != null) {
                job.setDurationSeconds(ChronoUnit.SECONDS.between(job.getStartTime(), job.getEndTime()));
            }
            etlJobRepository.save(job);
        });
    }
}
