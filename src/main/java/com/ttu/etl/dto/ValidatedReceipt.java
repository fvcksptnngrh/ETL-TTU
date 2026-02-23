package com.ttu.etl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidatedReceipt {
    private String transactionNo;
    private LocalDate trxDate;
    private String department;
    private String customerCode;
    private String customerName;
    private String customerAddress;

    @Builder.Default
    private List<ValidatedItem> items = new ArrayList<>();

    private BigDecimal calculatedSubtotal;
    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal taxTotal;
    private BigDecimal feeTotal;
    private BigDecimal grandTotal;

    private boolean isValid;

    @Builder.Default
    private List<String> validationErrors = new ArrayList<>();
}
