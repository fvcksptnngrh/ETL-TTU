package com.ttu.etl.dto;

import java.math.BigDecimal;

public record QuantityParseResult(BigDecimal qty, String unit, boolean hadFraction) {
}
