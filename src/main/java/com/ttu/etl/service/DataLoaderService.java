package com.ttu.etl.service;

import com.ttu.etl.dto.ValidatedItem;
import com.ttu.etl.dto.ValidatedReceipt;
import com.ttu.etl.entity.*;
import com.ttu.etl.repository.ErrorLogRepository;
import com.ttu.etl.repository.ProductRepository;
import com.ttu.etl.repository.TransactionHeaderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataLoaderService {

    private final TransactionHeaderRepository transactionHeaderRepository;
    private final ProductRepository productRepository;
    private final ErrorLogRepository errorLogRepository;

    @Value("${etl.batch-size:1000}")
    private int batchSize;

    @Transactional
    public int loadData(List<ValidatedReceipt> validatedReceipts, EtlJob etlJob, List<ErrorLog> errorLogs) {
        log.info("Starting data loading for {} receipts", validatedReceipts.size());

        // Pre-load ALL existing transaction numbers in 1 query (eliminates N SELECT queries)
        Set<String> existingTxnNos = new HashSet<>(transactionHeaderRepository.findAllTransactionNos());
        log.debug("Pre-loaded {} existing transaction numbers", existingTxnNos.size());

        // Pre-load existing product item codes only (projection, not full entities)
        Set<String> knownItemCodes = new HashSet<>(productRepository.findAllItemCodes());

        // Collect new products for batch save
        List<Product> newProducts = new ArrayList<>();

        int loadedCount = 0;
        int duplicateCount = 0;
        List<TransactionHeader> batch = new ArrayList<>();

        for (ValidatedReceipt receipt : validatedReceipts) {
            try {
                // Duplicate check using in-memory Set (no DB query)
                if (existingTxnNos.contains(receipt.getTransactionNo())) {
                    log.debug("Duplicate transaction detected: {}", receipt.getTransactionNo());
                    duplicateCount++;
                    errorLogs.add(ErrorLog.builder()
                            .etlJob(etlJob)
                            .rowNumber(0)
                            .fieldName("Transaction")
                            .errorDescription("Duplicate transaction_no: " + receipt.getTransactionNo())
                            .originalValue(receipt.getTransactionNo())
                            .errorType(ErrorLog.ErrorType.DUPLICATE_ERROR)
                            .build());
                    continue;
                }

                // Mark as known to catch duplicates within the same file
                existingTxnNos.add(receipt.getTransactionNo());

                TransactionHeader header = TransactionHeader.builder()
                        .transactionNo(receipt.getTransactionNo())
                        .trxDate(receipt.getTrxDate())
                        .department(receipt.getDepartment())
                        .customerCode(receipt.getCustomerCode())
                        .customerName(receipt.getCustomerName())
                        .customerAddress(receipt.getCustomerAddress())
                        .subtotal(receipt.getSubtotal())
                        .discountTotal(receipt.getDiscountTotal())
                        .taxTotal(receipt.getTaxTotal())
                        .feeTotal(receipt.getFeeTotal())
                        .grandTotal(receipt.getGrandTotal())
                        .isValid(receipt.isValid())
                        .etlJob(etlJob)
                        .items(new ArrayList<>())
                        .build();

                for (ValidatedItem vi : receipt.getItems()) {
                    TransactionItem item = TransactionItem.builder()
                            .transactionHeader(header)
                            .lineNo(vi.getLineNo())
                            .itemCode(vi.getItemCode())
                            .itemName(vi.getItemName())
                            .qty(vi.getQty())
                            .unit(vi.getUnit())
                            .unitPrice(vi.getUnitPrice())
                            .discountPct(vi.getDiscountPct())
                            .lineTotal(vi.getLineTotal())
                            .build();
                    header.getItems().add(item);

                    // Collect new products (no individual save)
                    if (vi.getItemCode() != null && !vi.getItemCode().trim().isEmpty()
                            && knownItemCodes.add(vi.getItemCode())) {
                        Product product = Product.builder()
                                .itemCode(vi.getItemCode())
                                .itemName(vi.getItemName())
                                .defaultUnit(vi.getUnit())
                                .etlJob(etlJob)
                                .build();
                        newProducts.add(product);
                    }
                }

                batch.add(header);
                loadedCount++;

                if (batch.size() >= batchSize) {
                    transactionHeaderRepository.saveAll(batch);
                    transactionHeaderRepository.flush();
                    log.debug("Saved batch of {} transactions", batch.size());
                    batch.clear();
                }

            } catch (Exception e) {
                log.error("Error loading receipt {}: {}", receipt.getTransactionNo(), e.getMessage(), e);
                errorLogs.add(ErrorLog.builder()
                        .etlJob(etlJob)
                        .rowNumber(0)
                        .fieldName("Transaction")
                        .errorDescription("Error loading receipt: " + e.getMessage())
                        .originalValue(receipt.getTransactionNo())
                        .errorType(ErrorLog.ErrorType.VALIDATION_ERROR)
                        .build());
            }
        }

        // Batch save remaining transactions
        if (!batch.isEmpty()) {
            transactionHeaderRepository.saveAll(batch);
            transactionHeaderRepository.flush();
            log.debug("Saved final batch of {} transactions", batch.size());
        }

        // Batch save all new products at once
        if (!newProducts.isEmpty()) {
            productRepository.saveAll(newProducts);
            log.debug("Batch saved {} new products", newProducts.size());
        }

        // Batch save all error logs
        if (!errorLogs.isEmpty()) {
            errorLogRepository.saveAll(errorLogs);
        }

        log.info("Data loading complete. Loaded: {}, Duplicates: {}, New products: {}",
                loadedCount, duplicateCount, newProducts.size());
        etlJob.setDuplicateRecords(duplicateCount);

        return loadedCount;
    }
}
