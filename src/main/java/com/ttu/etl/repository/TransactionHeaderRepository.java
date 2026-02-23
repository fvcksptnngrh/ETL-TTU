package com.ttu.etl.repository;

import com.ttu.etl.entity.TransactionHeader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionHeaderRepository extends JpaRepository<TransactionHeader, Long> {

    Optional<TransactionHeader> findByTransactionNo(String transactionNo);

    boolean existsByTransactionNo(String transactionNo);

    List<TransactionHeader> findByEtlJobId(Long etlJobId);

    long countByEtlJobId(Long etlJobId);

    long countByIsValidFalseAndEtlJobId(Long etlJobId);

    @Query("SELECT t.transactionNo FROM TransactionHeader t")
    List<String> findAllTransactionNos();

    @Query("SELECT t.transactionNo FROM TransactionHeader t WHERE t.isValid = false AND t.etlJob.id = :etlJobId")
    List<String> findFailedTransactionNosByEtlJobId(Long etlJobId);

    @Query("SELECT t.trxDate, COUNT(t) FROM TransactionHeader t GROUP BY t.trxDate ORDER BY t.trxDate DESC")
    List<Object[]> findTransactionDateSummary();

    @Query("SELECT t FROM TransactionHeader t LEFT JOIN FETCH t.etlJob LEFT JOIN FETCH t.items WHERE t.trxDate = :date ORDER BY t.transactionNo")
    List<TransactionHeader> findByTrxDate(LocalDate date);

    @Query("SELECT t FROM TransactionHeader t LEFT JOIN FETCH t.etlJob LEFT JOIN FETCH t.items WHERE t.transactionNo = :transactionNo")
    Optional<TransactionHeader> findByTransactionNoWithDetails(String transactionNo);
}
