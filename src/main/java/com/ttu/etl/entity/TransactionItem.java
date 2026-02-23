package com.ttu.etl.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_items", indexes = {
    @Index(name = "idx_item_transaction", columnList = "transaction_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private TransactionHeader transactionHeader;

    @Column(length = 20)
    private String lineNo;

    @Column(length = 100)
    private String itemCode;

    @Column(length = 300)
    private String itemName;

    @Column(precision = 19, scale = 4)
    private BigDecimal qty;

    @Column(length = 50)
    private String unit;

    @Column(precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 19, scale = 2)
    private BigDecimal discountPct;

    @Column(precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
