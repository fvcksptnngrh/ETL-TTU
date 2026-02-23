package com.ttu.etl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDto {

    private List<ProductSales> topProducts;
    private List<TrendPoint> trend;
    private List<ProductOption> productOptions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSales {
        private String itemCode;
        private String itemName;
        private String unit;
        private BigDecimal totalQty;
        private BigDecimal totalRevenue;
        private long txnCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String period;
        private BigDecimal totalQty;
        private BigDecimal totalRevenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductOption {
        private String itemCode;
        private String itemName;
    }
}
