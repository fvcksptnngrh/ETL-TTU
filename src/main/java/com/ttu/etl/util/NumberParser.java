package com.ttu.etl.util;

import com.ttu.etl.dto.QuantityParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class NumberParser {

    // "2,00 1/2 SAK" -> qty=2.00, unit="1/2 SAK"
    // "2,50 KG"      -> qty=2.50, unit="KG"
    private static final Pattern QTY_WITH_UNIT_PATTERN = Pattern.compile(
            "^\\s*(\\d+[.,]?\\d*)\\s+(\\S.*)\\s*$");

    // "2,50" or "3"
    private static final Pattern QTY_ONLY_PATTERN = Pattern.compile(
            "^\\s*(\\d+[.,]?\\d*)\\s*$");

    public BigDecimal parse(String value) throws ParseException {
        if (value == null || value.trim().isEmpty()) {
            throw new ParseException("Value is null or empty", 0);
        }

        String cleanValue = value.trim();

        // Remove currency symbols
        cleanValue = cleanValue.replaceAll("[Rp$€£¥]", "").trim();

        // Handle Indonesian format: 1.234.567,89
        if (cleanValue.matches(".*\\d+\\.\\d+\\.\\d+.*,\\d+.*")) {
            cleanValue = cleanValue.replaceAll("\\.", "").replace(",", ".");
        }
        // Handle Indonesian format without decimal: 1.234.567
        else if (cleanValue.matches(".*\\d+\\.\\d+\\.\\d+.*") && !cleanValue.contains(",")) {
            cleanValue = cleanValue.replaceAll("\\.", "");
        }
        // Handle format: 1,234,567.89
        else if (cleanValue.matches(".*\\d+,\\d+,\\d+.*\\.\\d+.*")) {
            cleanValue = cleanValue.replaceAll(",", "");
        }
        // Handle format with comma as decimal: 1234,89
        else if (cleanValue.matches("\\d+,\\d+")) {
            cleanValue = cleanValue.replace(",", ".");
        }
        // Handle format with only thousand separators (dot or comma)
        else if (cleanValue.matches("\\d{1,3}[.,]\\d{3}([.,]\\d{3})*")) {
            cleanValue = cleanValue.replaceAll("[.,]", "");
        }

        try {
            return new BigDecimal(cleanValue);
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid number format: " + value, 0);
        }
    }

    /**
     * Parse a quantity string that may contain a unit.
     * The fraction like "1/2" is part of the UNIT name, not a math operation.
     * Examples:
     *   "2,00 1/2 SAK" -> qty=2.00, unit="1/2 SAK"
     *   "2,50 KG"      -> qty=2.50, unit="KG"
     *   "2,50"          -> qty=2.50, unit=null
     *   "3"             -> qty=3,    unit=null
     */
    public QuantityParseResult parseQuantityWithUnit(String raw) throws ParseException {
        if (raw == null || raw.trim().isEmpty()) {
            throw new ParseException("Quantity is null or empty", 0);
        }

        String trimmed = raw.trim();

        // Try: number + unit (everything after the number is the unit)
        Matcher withUnit = QTY_WITH_UNIT_PATTERN.matcher(trimmed);
        if (withUnit.matches()) {
            String numberPart = withUnit.group(1);
            String unitPart = withUnit.group(2).trim();
            BigDecimal qty = parseSimpleDecimal(numberPart);
            return new QuantityParseResult(qty, unitPart, false);
        }

        // Try: just a number
        Matcher numberOnly = QTY_ONLY_PATTERN.matcher(trimmed);
        if (numberOnly.matches()) {
            BigDecimal qty = parseSimpleDecimal(numberOnly.group(1));
            return new QuantityParseResult(qty, null, false);
        }

        throw new ParseException("Cannot parse quantity: " + raw, 0);
    }

    private BigDecimal parseSimpleDecimal(String value) throws ParseException {
        String cleaned = value.replace(",", ".");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid number: " + value, 0);
        }
    }
}
