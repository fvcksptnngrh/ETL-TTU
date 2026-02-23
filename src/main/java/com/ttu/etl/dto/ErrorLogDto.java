package com.ttu.etl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorLogDto {
    private Integer rowNumber;
    private String fieldName;
    private String errorDescription;
    private String originalValue;
    private String errorType;
    private LocalDateTime createdAt;
}
