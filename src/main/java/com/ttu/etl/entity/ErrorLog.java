package com.ttu.etl.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "error_logs", indexes = {
    @Index(name = "idx_error_log_job", columnList = "etl_job_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etl_job_id", nullable = false)
    private EtlJob etlJob;

    @Column(nullable = false)
    private Integer rowNumber;

    @Column(nullable = false, length = 100)
    private String fieldName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String errorDescription;

    @Column(columnDefinition = "TEXT")
    private String originalValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ErrorType errorType;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ErrorType {
        VALIDATION_ERROR,
        FORMAT_ERROR,
        DUPLICATE_ERROR,
        NULL_VALUE_ERROR,
        BUSINESS_RULE_ERROR,
        QTY_FRACTION_INFO
    }
}
