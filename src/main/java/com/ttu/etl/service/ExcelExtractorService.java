package com.ttu.etl.service;

import com.github.pjfanning.xlsx.StreamingReader;
import com.ttu.etl.dto.ExtractedItem;
import com.ttu.etl.dto.ExtractedReceipt;
import com.ttu.etl.exception.EtlException;
import com.ttu.etl.service.excel.ColumnLayout;
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
        try (Workbook workbook = StreamingReader.builder()
                .rowCacheSize(200)
                .bufferSize(4096)
                .open(inputStream)) {

            ExtractionState state = new ExtractionState();
            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                state.rowNum = row.getRowNum();
                if (state.layout == null) {
                    tryDetectLayout(row, state);
                } else {
                    processRow(row, state);
                }
            }

            // Finalize last receipt
            if (state.currentReceipt != null) {
                state.receipts.add(state.currentReceipt);
            }

            log.info("Extracted {} receipts from Excel (streamed {} rows)",
                    state.receipts.size(), state.rowNum + 1);
            return state.receipts;
        }
    }

    /**
     * Phase 1: Scan rows until we hit an item-table header. Before that we
     * buffer any "transaction-header-looking" row so we can materialize it
     * once the layout is known.
     */
    private void tryDetectLayout(Row row, ExtractionState state) {
        Map<Integer, String> cells = extractAllCellValues(row);

        ColumnLayout detected = ColumnLayout.detect(cells);
        if (detected != null) {
            state.layout = detected;
            log.info("Auto-detected column layout at row {}: {}", state.rowNum + 1, detected);

            if (state.pendingTxnCells != null) {
                state.currentReceipt = buildReceiptFromMap(state.pendingTxnCells, state.pendingTxnRowNum, detected);
                log.debug("Built receipt from buffered txn header: {}", state.currentReceipt.getTransactionNo());
            }
            state.inItemSection = true;
            return;
        }

        // Buffer potential transaction header row (contains "/")
        boolean hasTxnLike = cells.values().stream().anyMatch(v -> v.contains("/"));
        if (hasTxnLike) {
            state.pendingTxnCells = cells;
            state.pendingTxnRowNum = state.rowNum;
        }
    }

    /**
     * Phase 2: With a known layout, decide whether the row is a transaction
     * header, an item-table header, a footer, or an item/summary row.
     */
    private void processRow(Row row, ExtractionState state) {
        ColumnLayout layout = state.layout;
        String txnColStr = getVal(row, layout.txnNumber());

        if (isTransactionHeaderRow(row, layout, txnColStr)) {
            if (state.currentReceipt != null) {
                state.receipts.add(state.currentReceipt);
            }
            state.currentReceipt = buildReceipt(row, state.rowNum, layout);
            state.inItemSection = false;
            log.debug("Found transaction header at row {}: txn={}", state.rowNum + 1, txnColStr);
            return;
        }

        if (isItemTableHeader(row, layout)) {
            state.inItemSection = true;
            return;
        }

        if (isFooterRow(txnColStr)) {
            state.inItemSection = false;
            if (state.currentReceipt != null) {
                state.currentReceipt.setDiscountTotal(getVal(row, layout.footerDiscount()));
                state.currentReceipt.setTaxTotal(getVal(row, layout.footerTax()));
                state.currentReceipt.setFeeTotal(getVal(row, layout.footerFee()));
                state.currentReceipt.setGrandTotal(getVal(row, layout.footerGrandTotal()));
            }
            return;
        }

        if (state.inItemSection && state.currentReceipt != null) {
            if (isItemDataRow(row, layout)) {
                ExtractedItem item = extractItemRow(row, state.rowNum + 1, layout);
                if (item != null) {
                    state.currentReceipt.getItems().add(item);
                }
            } else {
                String subtotalStr = getVal(row, layout.summarySubtotal());
                if (subtotalStr != null && isNumericLike(subtotalStr)) {
                    state.currentReceipt.setSubtotal(subtotalStr);
                }
            }
        }
    }

    private boolean isTransactionHeaderRow(Row row, ColumnLayout layout, String txnColStr) {
        if (txnColStr == null || !txnColStr.contains("/")) return false;
        String dateStr = getVal(row, layout.txnDate());
        return dateStr != null && !dateStr.isEmpty();
    }

    private boolean isFooterRow(String txnColStr) {
        if (txnColStr == null) return false;
        String lower = txnColStr.toLowerCase();
        return lower.startsWith("pot") || lower.startsWith("disc") || lower.startsWith("diskon");
    }

    private boolean isItemTableHeader(Row row, ColumnLayout layout) {
        String lineNoVal = getVal(row, layout.lineNo());
        String nameVal = getVal(row, layout.itemName());
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
        Cell lineNoCell = row.getCell(layout.lineNo());
        Cell itemCodeCell = row.getCell(layout.itemCode());
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
                    .lineNo(getVal(row, layout.lineNo()))
                    .itemCode(getVal(row, layout.itemCode()))
                    .itemName(getVal(row, layout.itemName()))
                    .quantity(getVal(row, layout.qty()))
                    .unit(getVal(row, layout.unit()))
                    .price(getVal(row, layout.price()))
                    .discount(getVal(row, layout.discount()))
                    .total(getVal(row, layout.total()))
                    .build();
        } catch (Exception e) {
            log.warn("Error extracting item at row {}: {}", rowNumber, e.getMessage());
            return null;
        }
    }

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

    /**
     * Mutable per-extraction state. Kept as a local holder so the row loop
     * stays readable and helper methods can share progress cleanly.
     */
    private static class ExtractionState {
        final List<ExtractedReceipt> receipts = new ArrayList<>();
        ColumnLayout layout;
        Map<Integer, String> pendingTxnCells;
        int pendingTxnRowNum = -1;
        ExtractedReceipt currentReceipt;
        boolean inItemSection;
        int rowNum;
    }
}
