package com.ttu.etl.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "etl_jobs", indexes = {
    @Index(name = "idx_etl_job_status", columnList = "status"),
    @Index(name = "idx_etl_job_created", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EtlJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String jobId;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 500)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Builder.Default
    @Column(nullable = false)
    private Integer totalRecords = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer extractedRecords = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer validRecords = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer failedRecords = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer duplicateRecords = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer loadedRecords = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationSeconds;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum JobStatus {
        PENDING,
        EXTRACTING,
        TRANSFORMING,
        LOADING,
        COMPLETED,
        FAILED
    }
}
