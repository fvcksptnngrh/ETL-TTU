package com.ttu.etl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedReceipt {
    private int headerRowNumber;
    private String transactionNo;
    private String date;
    private String department;
    private String customerCode;
    private String customerName;
    private String address;

    @Builder.Default
    private List<ExtractedItem> items = new ArrayList<>();

    // Summary row
    private String subtotal;

    // Footer row
    private String discountTotal;
    private String taxTotal;
    private String feeTotal;
    private String grandTotal;
}
