package com.ttu.etl.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transaction_no", columnList = "transactionNo"),
    @Index(name = "idx_transaction_date", columnList = "trxDate"),
    @Index(name = "idx_transaction_etl_job", columnList = "etl_job_id"),
    @Index(name = "idx_transaction_valid", columnList = "isValid")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String transactionNo;

    private LocalDate trxDate;

    @Column(length = 100)
    private String department;

    @Column(length = 100)
    private String customerCode;

    @Column(length = 200)
    private String customerName;

    @Column(length = 500)
    private String customerAddress;

    @Column(precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 19, scale = 2)
    private BigDecimal discountTotal;

    @Column(precision = 19, scale = 2)
    private BigDecimal taxTotal;

    @Column(precision = 19, scale = 2)
    private BigDecimal feeTotal;

    @Column(precision = 19, scale = 2)
    private BigDecimal grandTotal;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isValid = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etl_job_id")
    private EtlJob etlJob;

    @OneToMany(mappedBy = "transactionHeader", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TransactionItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
