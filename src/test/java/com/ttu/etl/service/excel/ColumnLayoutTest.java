package com.ttu.etl.service.excel;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ColumnLayoutTest {

    @Test
    void detect_returnsLayout_whenHeaderKeywordsPresent() {
        Map<Integer, String> cells = new HashMap<>();
        cells.put(0, "No.");
        cells.put(1, "Kode");
        cells.put(2, "Nama Item");
        cells.put(3, "Qty");
        cells.put(4, "Satuan");
        cells.put(5, "Harga");
        cells.put(6, "Pot");
        cells.put(7, "Total");

        ColumnLayout layout = ColumnLayout.detect(cells);

        assertNotNull(layout);
        assertEquals(0, layout.lineNo());
        assertEquals(1, layout.itemCode());
        assertEquals(2, layout.itemName());
        assertEquals(3, layout.qty());
        assertEquals(4, layout.unit());
        assertEquals(5, layout.price());
        assertEquals(6, layout.discount());
        assertEquals(7, layout.total());
    }

    @Test
    void detect_returnsNull_whenRequiredKeywordsMissing() {
        Map<Integer, String> cells = new HashMap<>();
        cells.put(0, "Tanggal");
        cells.put(1, "Customer");

        assertNull(ColumnLayout.detect(cells));
    }

    @Test
    void detect_returnsNull_whenLineNoMissing() {
        Map<Integer, String> cells = new HashMap<>();
        cells.put(0, "Kode");
        cells.put(1, "Nama");
        cells.put(2, "Qty");
        cells.put(3, "Total");

        assertNull(ColumnLayout.detect(cells));
    }

    @Test
    void derivedPositions_matchExpectedOffsets() {
        Map<Integer, String> cells = new HashMap<>();
        cells.put(0, "No.");
        cells.put(1, "Kode");
        cells.put(2, "Nama");
        cells.put(3, "Jml");
        cells.put(5, "Harga");
        cells.put(10, "Total");

        ColumnLayout layout = ColumnLayout.detect(cells);

        assertNotNull(layout);
        // txnNumber, txnDate, txnAddress follow item columns
        assertEquals(0, layout.txnNumber());
        assertEquals(2, layout.txnDate());
        assertEquals(5, layout.department());   // itemName + 3
        assertEquals(7, layout.customerCode()); // itemName + 5
        assertEquals(11, layout.customerName());
        // footer positions
        assertEquals(2, layout.footerDiscount());
        assertEquals(6, layout.footerTax());
        assertEquals(4, layout.footerFee());
        assertEquals(9, layout.footerGrandTotal());
    }

    @Test
    void isValid_requiresAtLeastFourColumnsIncludingLineNoAndItemName() {
        ColumnLayout layout = new ColumnLayout();
        layout.lineNo = 0;
        layout.itemName = 2;
        layout.qty = 3;
        assertFalse(layout.isValid()); // only 3

        layout.total = 7;
        assertTrue(layout.isValid()); // now 4
    }
}
