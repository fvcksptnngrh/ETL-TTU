package com.ttu.etl.service.excel;

import java.util.Map;

/**
 * Auto-detected column positions from the item table header row.
 * Transaction header and footer positions are derived from these.
 */
public class ColumnLayout {

    int lineNo = -1;
    int itemCode = -1;
    int itemName = -1;
    int qty = -1;
    int unit = -1;
    int price = -1;
    int discount = -1;
    int total = -1;

    public int lineNo() { return lineNo; }
    public int itemCode() { return itemCode; }
    public int itemName() { return itemName; }
    public int qty() { return qty; }
    public int unit() { return unit; }
    public int price() { return price; }
    public int discount() { return discount; }
    public int total() { return total; }

    // Transaction header positions — same column as item fields
    public int txnNumber() { return lineNo; }
    public int txnDate() { return itemName; }
    public int department() { return itemName + 3; }
    public int customerCode() { return itemName + 5; }
    public int customerName() { return itemName + 9; }
    public int txnAddress() { return price; }

    // Footer positions — derived from item column positions
    public int footerDiscount() { return lineNo + 2; }
    public int footerTax() { return itemName + 4; }
    public int footerFee() { return qty + 1; }
    public int footerGrandTotal() { return total - 1; }

    // Summary
    public int summarySubtotal() { return total; }

    public boolean isValid() {
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

    /**
     * Attempts to build a layout by matching item-table header keywords
     * against the given row's cell values. Returns null if the row does
     * not look like an item table header.
     */
    public static ColumnLayout detect(Map<Integer, String> cells) {
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

    @Override
    public String toString() {
        return String.format("lineNo=%d, itemCode=%d, itemName=%d, qty=%d, unit=%d, price=%d, discount=%d, total=%d",
                lineNo, itemCode, itemName, qty, unit, price, discount, total);
    }
}
