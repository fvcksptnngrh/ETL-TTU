package com.ttu.etl.service;

import com.ttu.etl.entity.Product;
import com.ttu.etl.entity.TransactionHeader;
import com.ttu.etl.entity.TransactionItem;
import com.ttu.etl.exception.JobNotFoundException;
import com.ttu.etl.repository.EtlJobRepository;
import com.ttu.etl.repository.ProductRepository;
import com.ttu.etl.repository.TransactionHeaderRepository;
import com.ttu.etl.repository.TransactionItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvExportService {

    private final TransactionHeaderRepository transactionHeaderRepository;
    private final TransactionItemRepository transactionItemRepository;
    private final ProductRepository productRepository;
    private final EtlJobRepository etlJobRepository;

    public byte[] exportTransactions(String jobId) {
        Long etlJobId = resolveEtlJobId(jobId);
        List<TransactionHeader> headers = transactionHeaderRepository.findByEtlJobId(etlJobId);

        StringBuilder sb = new StringBuilder();
        sb.append("transaction_no,trx_date,department,customer_code,customer_name,customer_address,subtotal,discount_total,tax_total,fee_total,grand_total,is_valid\n");

        for (TransactionHeader h : headers) {
            sb.append(csvEscape(h.getTransactionNo())).append(',');
            sb.append(h.getTrxDate() != null ? h.getTrxDate() : "").append(',');
            sb.append(csvEscape(h.getDepartment())).append(',');
            sb.append(csvEscape(h.getCustomerCode())).append(',');
            sb.append(csvEscape(h.getCustomerName())).append(',');
            sb.append(csvEscape(h.getCustomerAddress())).append(',');
            sb.append(h.getSubtotal() != null ? h.getSubtotal() : "").append(',');
            sb.append(h.getDiscountTotal() != null ? h.getDiscountTotal() : "").append(',');
            sb.append(h.getTaxTotal() != null ? h.getTaxTotal() : "").append(',');
            sb.append(h.getFeeTotal() != null ? h.getFeeTotal() : "").append(',');
            sb.append(h.getGrandTotal() != null ? h.getGrandTotal() : "").append(',');
            sb.append(h.getIsValid()).append('\n');
        }

        return sb.toString().getBytes();
    }

    public byte[] exportTransactionItems(String jobId) {
        Long etlJobId = resolveEtlJobId(jobId);
        List<TransactionHeader> headers = transactionHeaderRepository.findByEtlJobId(etlJobId);
        List<Long> headerIds = headers.stream().map(TransactionHeader::getId).collect(Collectors.toList());

        List<TransactionItem> items = transactionItemRepository.findByTransactionHeaderIdIn(headerIds);

        StringBuilder sb = new StringBuilder();
        sb.append("transaction_no,line_no,item_code,item_name,qty,unit,unit_price,discount_pct,line_total\n");

        for (TransactionItem item : items) {
            String txnNo = item.getTransactionHeader() != null ? item.getTransactionHeader().getTransactionNo() : "";
            sb.append(csvEscape(txnNo)).append(',');
            sb.append(csvEscape(item.getLineNo())).append(',');
            sb.append(csvEscape(item.getItemCode())).append(',');
            sb.append(csvEscape(item.getItemName())).append(',');
            sb.append(item.getQty() != null ? item.getQty() : "").append(',');
            sb.append(csvEscape(item.getUnit())).append(',');
            sb.append(item.getUnitPrice() != null ? item.getUnitPrice() : "").append(',');
            sb.append(item.getDiscountPct() != null ? item.getDiscountPct() : "").append(',');
            sb.append(item.getLineTotal() != null ? item.getLineTotal() : "").append('\n');
        }

        return sb.toString().getBytes();
    }

    public byte[] exportProducts() {
        List<Product> products = productRepository.findAll();

        StringBuilder sb = new StringBuilder();
        sb.append("item_code,item_name,default_unit\n");

        for (Product p : products) {
            sb.append(csvEscape(p.getItemCode())).append(',');
            sb.append(csvEscape(p.getItemName())).append(',');
            sb.append(csvEscape(p.getDefaultUnit())).append('\n');
        }

        return sb.toString().getBytes();
    }

    private Long resolveEtlJobId(String jobId) {
        return etlJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + jobId))
                .getId();
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
