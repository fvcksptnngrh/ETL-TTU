package com.ttu.etl.service;

import com.ttu.etl.dto.ValidatedItem;
import com.ttu.etl.dto.ValidatedReceipt;
import com.ttu.etl.entity.ErrorLog;
import com.ttu.etl.entity.EtlJob;
import com.ttu.etl.entity.Product;
import com.ttu.etl.entity.TransactionHeader;
import com.ttu.etl.repository.ErrorLogRepository;
import com.ttu.etl.repository.ProductRepository;
import com.ttu.etl.repository.TransactionHeaderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataLoaderServiceTest {

    @Mock TransactionHeaderRepository transactionHeaderRepository;
    @Mock ProductRepository productRepository;
    @Mock ErrorLogRepository errorLogRepository;

    @InjectMocks DataLoaderService dataLoaderService;

    private EtlJob etlJob;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dataLoaderService, "batchSize", 1000);
        etlJob = EtlJob.builder().jobId("test-job").build();
        when(transactionHeaderRepository.findAllTransactionNos()).thenReturn(Collections.emptyList());
        when(productRepository.findAllItemCodes()).thenReturn(Collections.emptyList());
    }

    @Test
    void loadData_persistsNewReceiptsAndProducts() {
        ValidatedReceipt receipt = receipt("TXN-001", "ITEM-A");
        List<ErrorLog> errorLogs = new ArrayList<>();

        int loaded = dataLoaderService.loadData(List.of(receipt), etlJob, errorLogs);

        assertThat(loaded).isEqualTo(1);
        verify(transactionHeaderRepository).saveAll(anyList());

        ArgumentCaptor<List<Product>> productCaptor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(productCaptor.capture());
        assertThat(productCaptor.getValue()).extracting(Product::getItemCode).containsExactly("ITEM-A");
    }

    @Test
    void loadData_skipsDuplicateTransactionNosFromDatabase() {
        when(transactionHeaderRepository.findAllTransactionNos()).thenReturn(List.of("TXN-DUP"));
        ValidatedReceipt receipt = receipt("TXN-DUP", "ITEM-X");
        List<ErrorLog> errorLogs = new ArrayList<>();

        int loaded = dataLoaderService.loadData(List.of(receipt), etlJob, errorLogs);

        assertThat(loaded).isZero();
        assertThat(errorLogs).hasSize(1);
        assertThat(errorLogs.get(0).getErrorType()).isEqualTo(ErrorLog.ErrorType.DUPLICATE_ERROR);
        verify(transactionHeaderRepository, never()).saveAll(any());
    }

    @Test
    void loadData_skipsDuplicateTransactionNosWithinSameFile() {
        ValidatedReceipt first = receipt("TXN-SAME", "ITEM-A");
        ValidatedReceipt second = receipt("TXN-SAME", "ITEM-B");
        List<ErrorLog> errorLogs = new ArrayList<>();

        int loaded = dataLoaderService.loadData(List.of(first, second), etlJob, errorLogs);

        assertThat(loaded).isEqualTo(1);
        assertThat(errorLogs).filteredOn(e -> e.getErrorType() == ErrorLog.ErrorType.DUPLICATE_ERROR).hasSize(1);
    }

    @Test
    void loadData_doesNotReAddKnownProducts() {
        when(productRepository.findAllItemCodes()).thenReturn(List.of("ITEM-A"));
        ValidatedReceipt receipt = receipt("TXN-1", "ITEM-A");
        List<ErrorLog> errorLogs = new ArrayList<>();

        dataLoaderService.loadData(List.of(receipt), etlJob, errorLogs);

        verify(productRepository, never()).saveAll(any());
    }

    private ValidatedReceipt receipt(String txnNo, String itemCode) {
        ValidatedItem item = ValidatedItem.builder()
                .lineNo("1")
                .itemCode(itemCode)
                .itemName("Test Item")
                .unit("PCS")
                .build();
        return ValidatedReceipt.builder()
                .transactionNo(txnNo)
                .trxDate(LocalDate.of(2025, 1, 1))
                .isValid(true)
                .items(new ArrayList<>(List.of(item)))
                .build();
    }
}
