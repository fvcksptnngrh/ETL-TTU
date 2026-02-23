package com.ttu.etl.service;

import com.github.pjfanning.xlsx.StreamingReader;
import com.ttu.etl.dto.ExtractedItem;
import com.ttu.etl.dto.ExtractedReceipt;
import com.ttu.etl.exception.EtlException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelExtractorService {

    /**
     * Auto-detected column positions from the item table header row.
     * Transaction header and footer positions are derived from these.
     */
    private static class ColumnLayout {
        // Detected from item table header keywords
        int lineNo = -1;
        int itemCode = -1;
        int itemName = -1;
        int qty = -1;
        int unit = -1;
        int price = -1;
        int discount = -1;
        int total = -1;

        // Transaction header - same column as item fields
        int txnNumber() { return lineNo; }
        int txnDate() { return itemName; }
        int department() { return itemName + 3; }
        int customerCode() { return itemName + 5; }
        int customerName() { return itemName + 9; }
        int txnAddress() { return price; }

        // Footer - derived from item column positions
        int footerDiscount() { return lineNo + 2; }
        int footerTax() { return itemName + 4; }
        int footerFee() { return qty + 1; }
        int footerGrandTotal() { return total - 1; }

        // Summary
        int summarySubtotal() { return total; }

        boolean isValid() {
            int count = 0;
            if (lineNo >= 0) count++;
            if (itemCode >= 0) count++;
            if (itemName >= 0) count++;
            if (qty >= 0) count++;
            if (unit >= 0) count++;
            if (price >= 0) count++;
            if (total >= 0) count++;
            return count >= 4 && lineNo >= 0 && itemName >= 0;
        }

        @Override
        public String toString() {
            return String.format("lineNo=%d, itemCode=%d, itemName=%d, qty=%d, unit=%d, price=%d, discount=%d, total=%d",
                    lineNo, itemCode, itemName, qty, unit, price, discount, total);
        }
    }

    public List<ExtractedReceipt> extractFromExcel(MultipartFile file) {
        log.info("Starting extraction from file: {}", file.getOriginalFilename());
        try (InputStream inputStream = file.getInputStream()) {
            return extractFromInputStream(inputStream);
        } catch (IOException e) {
            log.error("Error reading Excel file: {}", e.getMessage(), e);
            throw new EtlException("Failed to read Excel file: " + e.getMessage(), e);
        }
    }

    public List<ExtractedReceipt> extractFromExcel(Path filePath) {
        log.info("Starting extraction from file: {}", filePath.getFileName());
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return extractFromInputStream(inputStream);
        } catch (IOException e) {
            log.error("Error reading Excel file: {}", e.getMessage(), e);
            throw new EtlException("Failed to read Excel file: " + e.getMessage(), e);
        }
    }

    private List<ExtractedReceipt> extractFromInputStream(InputStream inputStream) throws IOException {
        List<ExtractedReceipt> receipts = new ArrayList<>();

        try (Workbook workbook = StreamingReader.builder()
                .rowCacheSize(200)
                .bufferSize(4096)
                .open(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            ColumnLayout layout = null;
            Map<Integer, String> pendingTxnCells = null;
            int pendingTxnRowNum = -1;
            ExtractedReceipt currentReceipt = null;
            boolean inItemSection = false;
            int rowNum = 0;

            for (Row row : sheet) {
                rowNum = row.getRowNum();

                // Phase 1: Before layout detection - scan for item table header
                if (layout == null) {
                    Map<Integer, String> cells = extractAllCellValues(row);

                    ColumnLayout detected = tryDetectLayout(cells);
                    if (detected != null) {
                        layout = detected;
                        log.info("Auto-detected column layout at row {}: {}", rowNum + 1, layout);

                        // Process buffered transaction header
                        if (pendingTxnCells != null) {
                            currentReceipt = buildReceiptFromMap(pendingTxnCells, pendingTxnRowNum, layout);
                            log.debug("Built receipt from buffered txn header: {}", currentReceipt.getTransactionNo());
                        }
                        inItemSection = true;
                        continue;
                    }

                    // Buffer potential transaction header row (contains "/")
                    boolean hasTxnLike = cells.values().stream().anyMatch(v -> v.contains("/"));
                    if (hasTxnLike) {
                        pendingTxnCells = cells;
                        pendingTxnRowNum = rowNum;
                    }
                    continue;
                }

                // Phase 2: Normal processing with detected layout
                String txnColStr = getVal(row, layout.txnNumber());

                // Detect transaction header row
                if (txnColStr != null && txnColStr.contains("/")) {
                    String dateStr = getVal(row, layout.txnDate());
                    if (dateStr != null && !dateStr.isEmpty()) {
                        if (currentReceipt != null) {
                            receipts.add(currentReceipt);
                        }
                        currentReceipt = buildReceipt(row, rowNum, layout);
                        inItemSection = false;
                        log.debug("Found transaction header at row {}: txn={}", rowNum + 1, txnColStr);
                        continue;
                    }
                }

                // Detect item table header row (subsequent receipts)
                if (isItemTableHeader(row, layout)) {
                    inItemSection = true;
                    continue;
                }

                // Detect footer row
                if (txnColStr != null && (txnColStr.toLowerCase().startsWith("pot")
                        || txnColStr.toLowerCase().startsWith("disc")
                        || txnColStr.toLowerCase().startsWith("diskon"))) {
                    inItemSection = false;
                    if (currentReceipt != null) {
                        currentReceipt.setDiscountTotal(getVal(row, layout.footerDiscount()));
                        currentReceipt.setTaxTotal(getVal(row, layout.footerTax()));
                        currentReceipt.setFeeTotal(getVal(row, layout.footerFee()));
                        currentReceipt.setGrandTotal(getVal(row, layout.footerGrandTotal()));
                    }
                    continue;
                }

                // Item data rows
                if (inItemSection && currentReceipt != null) {
                    if (isItemDataRow(row, layout)) {
                        ExtractedItem item = extractItemRow(row, rowNum + 1, layout);
                        if (item != null) {
                            currentReceipt.getItems().add(item);
                        }
                    } else {
                        String subtotalStr = getVal(row, layout.summarySubtotal());
                        if (subtotalStr != null && isNumericLike(subtotalStr)) {
                            currentReceipt.setSubtotal(subtotalStr);
                        }
                    }
                }
            }

            // Finalize last receipt
            if (currentReceipt != null) {
                receipts.add(currentReceipt);
            }

            log.info("Extracted {} receipts from Excel (streamed {} rows)", receipts.size(), rowNum + 1);
            return receipts;
        }
    }

    /**
     * Scans cell values for item table header keywords like "No.", "Nama Item", "Satuan", etc.
     * Returns a ColumnLayout if enough keywords are found (at least 4, including lineNo and itemName).
     */
    private ColumnLayout tryDetectLayout(Map<Integer, String> cells) {
        ColumnLayout layout = new ColumnLayout();

        for (Map.Entry<Integer, String> entry : cells.entrySet()) {
            String lower = entry.getValue().toLowerCase().trim();
            int col = entry.getKey();

            if (lower.matches("no\\.?")) {
                layout.lineNo = col;
            } else if (lower.contains("kd") || lower.contains("kode") || lower.contains("code")) {
                layout.itemCode = col;
            } else if (lower.contains("nama") || lower.contains("name")) {
                layout.itemName = col;
            } else if (lower.contains("jml") || lower.contains("qty") || lower.contains("jumlah")) {
                layout.qty = col;
            } else if (lower.contains("satuan") || lower.equals("sat") || lower.equals("unit")) {
                layout.unit = col;
            } else if (lower.contains("harga") || lower.contains("price")) {
                layout.price = col;
            } else if (lower.contains("pot") || lower.contains("disc")) {
                layout.discount = col;
            } else if (lower.contains("total")) {
                layout.total = col; // last match wins (rightmost column)
            }
        }

        return layout.isValid() ? layout : null;
    }

    /**
     * Checks if a row is an item table header (for subsequent receipts after layout detection).
     */
    private boolean isItemTableHeader(Row row, ColumnLayout layout) {
        String lineNoVal = getVal(row, layout.lineNo);
        String nameVal = getVal(row, layout.itemName);
        if (lineNoVal == null || nameVal == null) return false;
        String lower1 = lineNoVal.toLowerCase().trim();
        String lower2 = nameVal.toLowerCase().trim();
        return lower1.matches("no\\.?")
                && (lower2.contains("nama") || lower2.contains("item") || lower2.contains("name"));
    }

    private Map<Integer, String> extractAllCellValues(Row row) {
        Map<Integer, String> values = new HashMap<>();
        for (Cell cell : row) {
            String val = getCellValueAsString(cell);
            if (val != null) {
                values.put(cell.getColumnIndex(), val);
            }
        }
        return values;
    }

    private ExtractedReceipt buildReceiptFromMap(Map<Integer, String> cells, int rowNum, ColumnLayout layout) {
        return ExtractedReceipt.builder()
                .headerRowNumber(rowNum + 1)
                .transactionNo(cells.get(layout.txnNumber()))
                .date(cells.get(layout.txnDate()))
                .department(cells.get(layout.department()))
                .customerCode(cells.get(layout.customerCode()))
                .customerName(cells.get(layout.customerName()))
                .address(cells.get(layout.txnAddress()))
                .items(new ArrayList<>())
                .build();
    }

    private ExtractedReceipt buildReceipt(Row row, int rowNum, ColumnLayout layout) {
        return ExtractedReceipt.builder()
                .headerRowNumber(rowNum + 1)
                .transactionNo(getVal(row, layout.txnNumber()))
                .date(getVal(row, layout.txnDate()))
                .department(getVal(row, layout.department()))
                .customerCode(getVal(row, layout.customerCode()))
                .customerName(getVal(row, layout.customerName()))
                .address(getVal(row, layout.txnAddress()))
                .items(new ArrayList<>())
                .build();
    }

    private boolean isItemDataRow(Row row, ColumnLayout layout) {
        Cell lineNoCell = row.getCell(layout.lineNo);
        Cell itemCodeCell = row.getCell(layout.itemCode);
        if (lineNoCell == null || itemCodeCell == null) return false;
        if (lineNoCell.getCellType() == CellType.NUMERIC) {
            String itemCode = getCellValueAsString(itemCodeCell);
            return itemCode != null && !itemCode.isEmpty();
        }
        return false;
    }

    private ExtractedItem extractItemRow(Row row, int rowNumber, ColumnLayout layout) {
        try {
            return ExtractedItem.builder()
                    .rowNumber(rowNumber)
                    .lineNo(getVal(row, layout.lineNo))
                    .itemCode(getVal(row, layout.itemCode))
                    .itemName(getVal(row, layout.itemName))
                    .quantity(getVal(row, layout.qty))
                    .unit(getVal(row, layout.unit))
                    .price(getVal(row, layout.price))
                    .discount(getVal(row, layout.discount))
                    .total(getVal(row, layout.total))
                    .build();
        } catch (Exception e) {
            log.warn("Error extracting item at row {}: {}", rowNumber, e.getMessage());
            return null;
        }
    }

    /**
     * Safe cell value getter that handles negative column indices (undetected columns).
     */
    private String getVal(Row row, int colIndex) {
        if (colIndex < 0) return null;
        return getCellValueAsString(row.getCell(colIndex));
    }

    private boolean isNumericLike(String value) {
        if (value == null) return false;
        String cleaned = value.replaceAll("[.,\\s]", "");
        return cleaned.matches("\\d+");
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                String val = cell.getStringCellValue().trim();
                return val.isEmpty() ? null : val;
            case NUMERIC:
                try {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                    } else {
                        double numericValue = cell.getNumericCellValue();
                        if (numericValue == Math.floor(numericValue) && !Double.isInfinite(numericValue)) {
                            return String.valueOf((long) numericValue);
                        }
                        return String.valueOf(numericValue);
                    }
                } catch (NumberFormatException e) {
                    return null;
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            case BLANK:
                return null;
            default:
                return null;
        }
    }
}
