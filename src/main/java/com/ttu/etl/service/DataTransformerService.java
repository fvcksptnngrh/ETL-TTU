package com.ttu.etl.service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ttu.etl.dto.ExtractedItem;
import com.ttu.etl.dto.ExtractedReceipt;
import com.ttu.etl.dto.QuantityParseResult;
import com.ttu.etl.dto.ValidatedItem;
import com.ttu.etl.dto.ValidatedReceipt;
import com.ttu.etl.entity.ErrorLog;
import com.ttu.etl.entity.EtlJob;
import com.ttu.etl.util.DateParser;
import com.ttu.etl.util.NumberParser;
import com.ttu.etl.util.StringNormalizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataTransformerService {

    private final NumberParser numberParser;
    private final StringNormalizer stringNormalizer;
    private final DateParser dateParser;

    public List<ValidatedReceipt> transform(List<ExtractedReceipt> extractedReceipts, EtlJob etlJob,
                                             List<ErrorLog> errorLogs) {
        log.info("Starting transformation of {} receipts", extractedReceipts.size());
        List<ValidatedReceipt> results = new ArrayList<>();

        for (ExtractedReceipt extracted : extractedReceipts) {
            try {
                ValidatedReceipt validated = validateAndTransform(extracted, etlJob, errorLogs);
                results.add(validated);
            } catch (Exception e) {
                log.error("Unexpected error transforming receipt {}: {}", extracted.getTransactionNo(), e.getMessage());
                addErrorLog(errorLogs, etlJob, extracted.getHeaderRowNumber(), "GENERAL",
                        "Unexpected error: " + e.getMessage(), extracted.getTransactionNo(),
                        ErrorLog.ErrorType.VALIDATION_ERROR);

                // Still add a failed receipt
                ValidatedReceipt failed = ValidatedReceipt.builder()
                        .transactionNo(extracted.getTransactionNo())
                        .isValid(false)
                        .validationErrors(List.of("Unexpected error: " + e.getMessage()))
                        .build();
                results.add(failed);
            }
        }

        long validCount = results.stream().filter(ValidatedReceipt::isValid).count();
        log.info("Transformation complete. Valid: {}, Failed: {}", validCount, results.size() - validCount);
        return results;
    }

    private ValidatedReceipt validateAndTransform(ExtractedReceipt extracted, EtlJob etlJob,
                                                    List<ErrorLog> errorLogs) {
        List<String> validationErrors = new ArrayList<>();
        ValidatedReceipt.ValidatedReceiptBuilder builder = ValidatedReceipt.builder();

        builder.transactionNo(extracted.getTransactionNo());

        // Parse date
        LocalDate trxDate = parseDate(extracted.getDate());
        if (trxDate == null) {
            validationErrors.add("Invalid date: " + extracted.getDate());
            addErrorLog(errorLogs, etlJob, extracted.getHeaderRowNumber(), "Date",
                    "Cannot parse date", extracted.getDate(), ErrorLog.ErrorType.FORMAT_ERROR);
        }
        builder.trxDate(trxDate);

        builder.department(extracted.getDepartment());
        builder.customerCode(extracted.getCustomerCode());
        builder.customerName(extracted.getCustomerName() != null
                ? stringNormalizer.normalize(extracted.getCustomerName()) : null);
        builder.customerAddress(extracted.getAddress());

        // Transform items
        List<ValidatedItem> validatedItems = new ArrayList<>();
        BigDecimal calculatedSubtotal = BigDecimal.ZERO;

        for (ExtractedItem item : extracted.getItems()) {
            ValidatedItem validatedItem = transformItem(item, extracted, etlJob, errorLogs, validationErrors);
            if (validatedItem != null) {
                validatedItems.add(validatedItem);
                if (validatedItem.getLineTotal() != null) {
                    calculatedSubtotal = calculatedSubtotal.add(validatedItem.getLineTotal());
                }
            }
        }
        builder.items(validatedItems);
        builder.calculatedSubtotal(calculatedSubtotal);

        // Parse footer amounts
        BigDecimal subtotal = parseOptionalNumber(extracted.getSubtotal());
        BigDecimal discountTotal = parseOptionalNumber(extracted.getDiscountTotal());
        BigDecimal taxTotal = parseOptionalNumber(extracted.getTaxTotal());
        BigDecimal feeTotal = parseOptionalNumber(extracted.getFeeTotal());
        BigDecimal grandTotal = parseOptionalNumber(extracted.getGrandTotal());

        builder.subtotal(subtotal);
        builder.discountTotal(discountTotal);
        builder.taxTotal(taxTotal);
        builder.feeTotal(feeTotal);
        builder.grandTotal(grandTotal);

        // Validate grand total: grand_total ≈ subtotal - discount + tax + fee
        if (grandTotal != null && subtotal != null) {
            BigDecimal disc = discountTotal != null ? discountTotal : BigDecimal.ZERO;
            BigDecimal tax = taxTotal != null ? taxTotal : BigDecimal.ZERO;
            BigDecimal fee = feeTotal != null ? feeTotal : BigDecimal.ZERO;
            BigDecimal expected = subtotal.subtract(disc).add(tax).add(fee);
            BigDecimal diff = expected.subtract(grandTotal).abs();

            if (diff.compareTo(new BigDecimal("1.00")) > 0) {
                String info = String.format(
                        "Grand total rounding: calculated=%s (subtotal=%s - discount=%s + tax=%s + fee=%s), actual=%s, diff=%s",
                        expected, subtotal, disc, tax, fee, grandTotal, diff);
                addErrorLog(errorLogs, etlJob, extracted.getHeaderRowNumber(), "Grand Total",
                        info, String.valueOf(grandTotal), ErrorLog.ErrorType.QTY_FRACTION_INFO);
            }
        }

        boolean isValid = validationErrors.isEmpty();
        builder.isValid(isValid);
        builder.validationErrors(validationErrors);

        return builder.build();
    }

    private ValidatedItem transformItem(ExtractedItem item, ExtractedReceipt receipt,
                                         EtlJob etlJob, List<ErrorLog> errorLogs,
                                         List<String> receiptErrors) {
        try {
            ValidatedItem.ValidatedItemBuilder ib = ValidatedItem.builder();
            ib.lineNo(item.getLineNo());
            ib.itemCode(item.getItemCode());
            ib.itemName(item.getItemName() != null ? stringNormalizer.normalize(item.getItemName()) : item.getItemName());

            // Parse quantity and unit
            BigDecimal qty = null;
            String unit = item.getUnit();

            if (item.getQuantity() != null && !item.getQuantity().trim().isEmpty()) {
                try {
                    QuantityParseResult qtyResult = numberParser.parseQuantityWithUnit(item.getQuantity());
                    qty = qtyResult.qty();
                    if (qtyResult.unit() != null) {
                        unit = qtyResult.unit();
                    }
                } catch (ParseException e) {
                    try {
                        qty = numberParser.parse(item.getQuantity());
                    } catch (ParseException e2) {
                        receiptErrors.add("Cannot parse qty at row " + item.getRowNumber() + ": " + item.getQuantity());
                        addErrorLog(errorLogs, etlJob, item.getRowNumber(), "Quantity",
                                "Invalid quantity format", item.getQuantity(), ErrorLog.ErrorType.FORMAT_ERROR);
                    }
                }
            }

            ib.qty(qty);
            ib.unit(unit);
            ib.qtyHadFraction(false);

            // Parse price
            BigDecimal price = null;
            if (item.getPrice() != null && !item.getPrice().trim().isEmpty()) {
                try {
                    price = numberParser.parse(item.getPrice());
                } catch (ParseException e) {
                    receiptErrors.add("Cannot parse price at row " + item.getRowNumber() + ": " + item.getPrice());
                    addErrorLog(errorLogs, etlJob, item.getRowNumber(), "Price",
                            "Invalid price format", item.getPrice(), ErrorLog.ErrorType.FORMAT_ERROR);
                }
            }
            ib.unitPrice(price);

            // Parse discount
            BigDecimal discount = BigDecimal.ZERO;
            if (item.getDiscount() != null && !item.getDiscount().trim().isEmpty()) {
                try {
                    discount = numberParser.parse(item.getDiscount());
                } catch (ParseException e) {
                    // Non-critical
                    log.debug("Could not parse discount at row {}: {}", item.getRowNumber(), item.getDiscount());
                }
            }
            ib.discountPct(discount);

            // Parse line total
            BigDecimal lineTotal = null;
            if (item.getTotal() != null && !item.getTotal().trim().isEmpty()) {
                try {
                    lineTotal = numberParser.parse(item.getTotal());
                } catch (ParseException e) {
                    receiptErrors.add("Cannot parse total at row " + item.getRowNumber() + ": " + item.getTotal());
                    addErrorLog(errorLogs, etlJob, item.getRowNumber(), "Line Total",
                            "Invalid total format", item.getTotal(), ErrorLog.ErrorType.FORMAT_ERROR);
                }
            }
            ib.lineTotal(lineTotal);

            return ib.build();
        } catch (Exception e) {
            log.warn("Error transforming item at row {}: {}", item.getRowNumber(), e.getMessage());
            receiptErrors.add("Error transforming item at row " + item.getRowNumber());
            return null;
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            return dateParser.parse(dateStr.trim());
        }
    }

    private BigDecimal parseOptionalNumber(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return numberParser.parse(value);
        } catch (ParseException e) {
            log.debug("Could not parse optional number: {}", value);
            return null;
        }
    }

    private void addErrorLog(List<ErrorLog> errorLogs, EtlJob etlJob, int rowNumber,
                             String fieldName, String errorDescription, String originalValue,
                             ErrorLog.ErrorType errorType) {
        ErrorLog errorLog = ErrorLog.builder()
                .etlJob(etlJob)
                .rowNumber(rowNumber)
                .fieldName(fieldName)
                .errorDescription(errorDescription)
                .originalValue(originalValue)
                .errorType(errorType)
                .build();
        errorLogs.add(errorLog);
    }
}
