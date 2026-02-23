package com.ttu.etl.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDto {

    private String itemCode;
    private String itemName;
    private String defaultUnit;
    private String firstSeenInFile;
    private boolean hasVariations;
    private List<Variation> variations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Variation {
        private String itemName;
        private String unit;
        private String fileName;
    }
}
