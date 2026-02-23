package com.ttu.etl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidatedItem {
    private String lineNo;
    private String itemCode;
    private String itemName;
    private BigDecimal qty;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal discountPct;
    private BigDecimal lineTotal;
    private boolean qtyHadFraction;
}
