package com.ttu.etl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedItem {
    private int rowNumber;
    private String lineNo;
    private String itemCode;
    private String itemName;
    private String quantity;
    private String unit;
    private String price;
    private String discount;
    private String total;
}
