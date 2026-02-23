package com.ttu.etl.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ttu.etl.entity.EtlJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobResponse {

    private String jobId;
    private String fileName;
    private String status;

    // stats from EtlJob
    private Integer totalRecords;
    private Integer extractedRecords;
    private Integer validRecords;
    private Integer failedRecords;
    private Integer duplicateRecords;
    private Integer loadedRecords;
    private String errorMessage;

    // timing
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationSeconds;
    private LocalDateTime createdAt;

    // detail-only fields (null in list view)
    private Long transactionCount;
    private Long productCount;
    private Long errorCount;
    private List<ErrorLogDto> errors;
    private List<String> failedTransactionNos;
    private Long qtyFractionIssues;

    public static JobResponse fromEntity(EtlJob job) {
        return JobResponse.builder()
                .jobId(job.getJobId())
                .fileName(job.getFileName())
                .status(job.getStatus().name())
                .totalRecords(job.getTotalRecords())
                .extractedRecords(job.getExtractedRecords())
                .validRecords(job.getValidRecords())
                .failedRecords(job.getFailedRecords())
                .duplicateRecords(job.getDuplicateRecords())
                .loadedRecords(job.getLoadedRecords())
                .errorMessage(job.getErrorMessage())
                .startTime(job.getStartTime())
                .endTime(job.getEndTime())
                .durationSeconds(job.getDurationSeconds())
                .createdAt(job.getCreatedAt())
                .build();
    }
}
