package com.ttu.etl.controller;

import com.ttu.etl.dto.JobResponse;
import com.ttu.etl.dto.ProductDto;
import com.ttu.etl.dto.TransactionDto;
import com.ttu.etl.dto.UploadResponse;
import com.ttu.etl.entity.TransactionHeader;
import com.ttu.etl.repository.TransactionHeaderRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private final ProductService productService;
    private final TransactionHeaderRepository transactionHeaderRepository;

    // ─── ETL JOBS ────────────────────────────────────────────────────────────

    @Operation(summary = "Upload file Excel",
            description = "Upload file .xlsx untuk diproses ETL. Proses berjalan async — langsung dapat jobId tanpa menunggu selesai.")
    @PostMapping("/jobs")
    public ResponseEntity<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        log.info("Received file upload: {}", file.getOriginalFilename());
        return ResponseEntity.ok(etlService.uploadAndProcess(file));
    }

    @Operation(summary = "List semua ETL jobs",
            description = "Menampilkan semua job yang pernah diproses beserta statistiknya. Mendukung pagination (?page=0&size=10).")
    @GetMapping("/jobs")
    public ResponseEntity<Page<JobResponse>> getJobs(Pageable pageable) {
        return ResponseEntity.ok(etlService.getJobs(pageable));
    }

    @Operation(summary = "Detail satu ETL job",
            description = "Menampilkan status, statistik, dan daftar error dari satu job.")
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobResponse> getJobDetail(@PathVariable String jobId) {
        return ResponseEntity.ok(etlService.getJobDetail(jobId));
    }

    // ─── PRODUCTS ────────────────────────────────────────────────────────────

    @Operation(summary = "List produk",
            description = "Menampilkan semua produk yang ter-ekstrak. Tambahkan ?inconsistencies=true untuk filter produk dengan nama/satuan tidak konsisten.")
    @Tag(name = "Products")
    @GetMapping("/products")
    public ResponseEntity<List<ProductDto>> getProducts(
            @RequestParam(defaultValue = "false") boolean inconsistencies) {
        return ResponseEntity.ok(productService.getProducts(inconsistencies));
    }

    @Operation(summary = "Detail produk",
            description = "Menampilkan detail satu produk berdasarkan kode item.")
    @Tag(name = "Products")
    @GetMapping("/products/{itemCode}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable String itemCode) {
        return productService.getProduct(itemCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── TRANSACTIONS ────────────────────────────────────────────────────────

    @Operation(summary = "Daftar tanggal transaksi",
            description = "Menampilkan semua tanggal yang memiliki data transaksi beserta jumlahnya.")
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

    @Operation(summary = "List transaksi per tanggal",
            description = "Menampilkan semua transaksi pada tanggal tertentu. Format: YYYY-MM-DD.")
    @Tag(name = "Transactions")
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionDto>> getTransactionsByDate(
            @Parameter(description = "Tanggal transaksi (format: YYYY-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
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

    @Operation(summary = "Detail transaksi",
            description = "Menampilkan detail lengkap satu transaksi beserta semua item di dalamnya.")
    @Tag(name = "Transactions")
    @GetMapping("/transactions/detail")
    public ResponseEntity<TransactionDto> getTransactionDetail(
            @Parameter(description = "Nomor transaksi (contoh: 000001/KSR/UTM/1025)")
            @RequestParam String no) {
        return transactionHeaderRepository.findByTransactionNoWithDetails(no)
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
}
