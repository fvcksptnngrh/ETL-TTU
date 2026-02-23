package com.ttu.etl.controller;

import com.ttu.etl.dto.AnalyticsDto;
import com.ttu.etl.dto.JobResponse;
import com.ttu.etl.dto.ProductDto;
import com.ttu.etl.dto.TransactionDto;
import com.ttu.etl.dto.UploadResponse;
import com.ttu.etl.entity.TransactionHeader;
import com.ttu.etl.repository.TransactionHeaderRepository;
import com.ttu.etl.repository.TransactionItemRepository;
import com.ttu.etl.service.CsvExportService;
import com.ttu.etl.service.EtlService;
import com.ttu.etl.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/etl")
@RequiredArgsConstructor
@Tag(name = "ETL Jobs", description = "Upload file dan pantau status proses ETL")
public class EtlController {

    private final EtlService etlService;
    private final CsvExportService csvExportService;
    private final ProductService productService;
    private final TransactionHeaderRepository transactionHeaderRepository;
    private final TransactionItemRepository transactionItemRepository;

    // POST /etl/jobs — Upload & process file
    @Operation(summary = "Upload file Excel", description = "Upload file .xlsx untuk diproses ETL. Proses berjalan async — langsung dapat jobId tanpa menunggu selesai.")
    @Tag(name = "ETL Jobs")
    @PostMapping("/jobs")
    public ResponseEntity<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        log.info("Received file upload: {}", file.getOriginalFilename());
        return ResponseEntity.ok(etlService.uploadAndProcess(file));
    }

    @Operation(summary = "List semua ETL jobs", description = "Menampilkan semua job yang pernah diproses beserta statistiknya. Mendukung pagination (?page=0&size=10).")
    @Tag(name = "ETL Jobs")
    @GetMapping("/jobs")
    public ResponseEntity<Page<JobResponse>> getJobs(Pageable pageable) {
        return ResponseEntity.ok(etlService.getJobs(pageable));
    }

    @Operation(summary = "Detail satu ETL job", description = "Menampilkan status, statistik, dan daftar error dari satu job. Gunakan jobId yang didapat dari response upload.")
    @Tag(name = "ETL Jobs")
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobResponse> getJobDetail(@PathVariable String jobId) {
        return ResponseEntity.ok(etlService.getJobDetail(jobId));
    }

    @Operation(summary = "Export data ke CSV", description = "Download hasil ETL dalam format CSV. Gunakan `type=transactions` untuk data transaksi atau `type=items` untuk detail item per transaksi.")
    @Tag(name = "Export")
    @GetMapping("/jobs/{jobId}/export")
    public ResponseEntity<byte[]> exportCsv(@PathVariable String jobId,
                                            @Parameter(description = "Tipe export: `transactions` atau `items`") @RequestParam(defaultValue = "transactions") String type) {
        byte[] csv;
        String filename;

        if ("items".equalsIgnoreCase(type)) {
            csv = csvExportService.exportTransactionItems(jobId);
            filename = "items_" + jobId + ".csv";
        } else {
            csv = csvExportService.exportTransactions(jobId);
            filename = "transactions_" + jobId + ".csv";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(csv.length);
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @Operation(summary = "List produk", description = "Menampilkan semua produk yang ter-ekstrak. Tambahkan `?inconsistencies=true` untuk filter produk dengan nama/satuan tidak konsisten antar file. Tambahkan `?format=csv` untuk download.")
    @Tag(name = "Products")
    @GetMapping("/products")
    public ResponseEntity<?> getProducts(
            @RequestParam(defaultValue = "false") boolean inconsistencies,
            @RequestParam(required = false) String format) {

        if ("csv".equalsIgnoreCase(format)) {
            byte[] csv = csvExportService.exportProducts();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDispositionFormData("attachment", "products.csv");
            headers.setContentLength(csv.length);
            return ResponseEntity.ok().headers(headers).body(csv);
        }

        List<ProductDto> products = productService.getProducts(inconsistencies);
        return ResponseEntity.ok(products);
    }

    @Operation(summary = "Detail produk", description = "Menampilkan detail satu produk berdasarkan kode item.")
    @Tag(name = "Products")
    @GetMapping("/products/{itemCode}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable String itemCode) {
        return productService.getProduct(itemCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Daftar tanggal transaksi", description = "Menampilkan semua tanggal yang memiliki data transaksi beserta jumlahnya. Berguna untuk kalender / filter tanggal.")
    @Tag(name = "Transactions")
    @GetMapping("/transactions/dates")
    public ResponseEntity<List<TransactionDto.DateSummary>> getTransactionDates() {
        List<Object[]> results = transactionHeaderRepository.findTransactionDateSummary();
        List<TransactionDto.DateSummary> summaries = results.stream()
                .map(row -> TransactionDto.DateSummary.builder()
                        .date((LocalDate) row[0])
                        .count((Long) row[1])
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(summaries);
    }

    @Operation(summary = "List transaksi per tanggal", description = "Menampilkan semua transaksi pada tanggal tertentu. Format tanggal: `YYYY-MM-DD` (contoh: `2025-01-15`).")
    @Tag(name = "Transactions")
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionDto>> getTransactionsByDate(
            @Parameter(description = "Tanggal transaksi (format: YYYY-MM-DD)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<TransactionHeader> transactions = transactionHeaderRepository.findByTrxDate(date);
        List<TransactionDto> dtos = transactions.stream()
                .map(t -> TransactionDto.builder()
                        .transactionNo(t.getTransactionNo())
                        .trxDate(t.getTrxDate())
                        .department(t.getDepartment())
                        .customerCode(t.getCustomerCode())
                        .customerName(t.getCustomerName())
                        .grandTotal(t.getGrandTotal())
                        .isValid(t.getIsValid())
                        .fileName(t.getEtlJob() != null ? t.getEtlJob().getFileName() : null)
                        .itemCount(t.getItems().size())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @Operation(summary = "Detail transaksi", description = "Menampilkan detail lengkap satu transaksi beserta semua item di dalamnya. Contoh no: `000001/KSR/UTM/1025`.")
    @Tag(name = "Transactions")
    @GetMapping("/transactions/detail")
    public ResponseEntity<TransactionDto> getTransactionDetail(
            @Parameter(description = "Nomor transaksi (contoh: 000001/KSR/UTM/1025)") @RequestParam String no) {
        String transactionNo = no;
        return transactionHeaderRepository.findByTransactionNoWithDetails(transactionNo)
                .map(t -> {
                    List<TransactionDto.ItemDto> items = t.getItems().stream()
                            .map(i -> TransactionDto.ItemDto.builder()
                                    .no(i.getLineNo() != null ? Integer.parseInt(i.getLineNo()) : 0)
                                    .itemCode(i.getItemCode())
                                    .itemName(i.getItemName())
                                    .quantity(i.getQty())
                                    .unit(i.getUnit())
                                    .price(i.getUnitPrice())
                                    .discount(i.getDiscountPct())
                                    .total(i.getLineTotal())
                                    .build())
                            .collect(Collectors.toList());

                    return ResponseEntity.ok(TransactionDto.builder()
                            .transactionNo(t.getTransactionNo())
                            .trxDate(t.getTrxDate())
                            .department(t.getDepartment())
                            .customerCode(t.getCustomerCode())
                            .customerName(t.getCustomerName())
                            .grandTotal(t.getGrandTotal())
                            .isValid(t.getIsValid())
                            .fileName(t.getEtlJob() != null ? t.getEtlJob().getFileName() : null)
                            .itemCount(items.size())
                            .items(items)
                            .subtotal(t.getSubtotal())
                            .discount(t.getDiscountTotal())
                            .tax(t.getTaxTotal())
                            .build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Top produk terlaris", description = "Menampilkan produk terlaris berdasarkan total qty dan revenue. Bisa difilter by rentang tanggal.")
    @Tag(name = "Analytics")
    @GetMapping("/analytics/top-products")
    public ResponseEntity<List<AnalyticsDto.ProductSales>> getTopProducts(
            @Parameter(description = "Tanggal mulai (YYYY-MM-DD)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Tanggal akhir (YYYY-MM-DD)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Object[]> rows = transactionItemRepository.findProductSalesSummary(startDate, endDate);
        List<AnalyticsDto.ProductSales> result = rows.stream()
                .map(r -> AnalyticsDto.ProductSales.builder()
                        .itemCode((String) r[0])
                        .itemName((String) r[1])
                        .unit((String) r[2])
                        .totalQty(toBigDecimal(r[3]))
                        .totalRevenue(toBigDecimal(r[4]))
                        .txnCount(((Number) r[5]).longValue())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Trend penjualan", description = "Menampilkan trend penjualan berdasarkan periode. `granularity`: `daily`, `weekly`, atau `monthly`. Bisa difilter per produk menggunakan `itemCode`.")
    @Tag(name = "Analytics")
    @GetMapping("/analytics/trend")
    public ResponseEntity<List<AnalyticsDto.TrendPoint>> getSalesTrend(
            @Parameter(description = "Kode item produk (opsional, kosongkan untuk semua produk)") @RequestParam(required = false) String itemCode,
            @Parameter(description = "Granularitas waktu: `daily`, `weekly`, `monthly`") @RequestParam(defaultValue = "monthly") String granularity,
            @Parameter(description = "Tanggal mulai (YYYY-MM-DD)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Tanggal akhir (YYYY-MM-DD)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Object[]> rows;
        if ("daily".equalsIgnoreCase(granularity)) {
            rows = transactionItemRepository.findDailySalesTrend(itemCode, startDate, endDate);
        } else if ("weekly".equalsIgnoreCase(granularity)) {
            rows = transactionItemRepository.findWeeklySalesTrend(itemCode, startDate, endDate);
        } else {
            rows = transactionItemRepository.findMonthlySalesTrend(itemCode, startDate, endDate);
        }
        List<AnalyticsDto.TrendPoint> result = rows.stream()
                .map(r -> AnalyticsDto.TrendPoint.builder()
                        .period(toDateString(r[0]))
                        .totalQty(toBigDecimal(r[1]))
                        .totalRevenue(toBigDecimal(r[2]))
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Daftar produk untuk filter", description = "Menampilkan semua item code + nama produk yang tersedia. Digunakan sebagai pilihan dropdown untuk filter analytics.")
    @Tag(name = "Analytics")
    @GetMapping("/analytics/product-options")
    public ResponseEntity<List<AnalyticsDto.ProductOption>> getProductOptions() {
        List<Object[]> rows = transactionItemRepository.findDistinctProducts();
        List<AnalyticsDto.ProductOption> result = rows.stream()
                .map(r -> AnalyticsDto.ProductOption.builder()
                        .itemCode((String) r[0])
                        .itemName((String) r[1])
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        return new BigDecimal(val.toString());
    }

    private String toDateString(Object val) {
        if (val == null) return "";
        if (val instanceof Date) return ((Date) val).toLocalDate().toString();
        if (val instanceof Timestamp) return ((Timestamp) val).toLocalDateTime().toLocalDate().withDayOfMonth(1).toString();
        if (val instanceof LocalDate) return val.toString();
        return val.toString();
    }
}
