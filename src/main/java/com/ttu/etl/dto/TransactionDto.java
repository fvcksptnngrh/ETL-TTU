package com.ttu.etl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionDto {

    private String transactionNo;
    private LocalDate trxDate;
    private String department;
    private String customerCode;
    private String customerName;
    private BigDecimal grandTotal;
    private Boolean isValid;
    private String fileName;
    private int itemCount;

    // Detail fields (null in list view)
    private List<ItemDto> items;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal tax;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDto {
        private int no;
        private String itemCode;
        private String itemName;
        private BigDecimal quantity;
        private String unit;
        private BigDecimal price;
        private BigDecimal discount;
        private BigDecimal total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateSummary {
        private LocalDate date;
        private long count;
    }
}
