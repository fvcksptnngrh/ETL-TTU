package com.ttu.etl.repository;

import com.ttu.etl.entity.TransactionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionItemRepository extends JpaRepository<TransactionItem, Long> {

    List<TransactionItem> findByTransactionHeaderId(Long transactionId);

    List<TransactionItem> findByTransactionHeaderIdIn(List<Long> transactionIds);

    long countByTransactionHeaderEtlJobId(Long etlJobId);

    @Query(value = """
            SELECT ti.item_code, ti.item_name, ti.unit, ej.file_name
            FROM transaction_items ti
            JOIN transactions t ON ti.transaction_id = t.id
            JOIN etl_jobs ej ON t.etl_job_id = ej.id
            GROUP BY ti.item_code, ti.item_name, ti.unit, ej.file_name
            ORDER BY ti.item_code
            """, nativeQuery = true)
    List<Object[]> findDistinctProductVariations();

    // Top products by total qty sold
    @Query(value = """
            SELECT ti.item_code, ti.item_name, ti.unit,
                   SUM(ti.qty) AS total_qty,
                   SUM(ti.line_total) AS total_revenue,
                   COUNT(DISTINCT ti.transaction_id) AS txn_count
            FROM transaction_items ti
            JOIN transactions t ON ti.transaction_id = t.id
            WHERE (:startDate IS NULL OR t.trx_date >= :startDate)
              AND (:endDate IS NULL OR t.trx_date <= :endDate)
            GROUP BY ti.item_code, ti.item_name, ti.unit
            ORDER BY total_qty DESC
            """, nativeQuery = true)
    List<Object[]> findProductSalesSummary(LocalDate startDate, LocalDate endDate);

    // Monthly sales trend for a specific product (or all if itemCode is null)
    @Query(value = """
            SELECT TO_CHAR(t.trx_date, 'YYYY-MM') AS month,
                   SUM(ti.qty) AS total_qty,
                   SUM(ti.line_total) AS total_revenue
            FROM transaction_items ti
            JOIN transactions t ON ti.transaction_id = t.id
            WHERE (:itemCode IS NULL OR ti.item_code = :itemCode)
              AND (:startDate IS NULL OR t.trx_date >= :startDate)
              AND (:endDate IS NULL OR t.trx_date <= :endDate)
            GROUP BY TO_CHAR(t.trx_date, 'YYYY-MM')
            ORDER BY month
            """, nativeQuery = true)
    List<Object[]> findMonthlySalesTrend(String itemCode, LocalDate startDate, LocalDate endDate);

    // Weekly sales trend for a specific product
    @Query(value = """
            SELECT TO_CHAR(DATE_TRUNC('week', t.trx_date), 'YYYY-MM-DD') AS week_start,
                   SUM(ti.qty) AS total_qty,
                   SUM(ti.line_total) AS total_revenue
            FROM transaction_items ti
            JOIN transactions t ON ti.transaction_id = t.id
            WHERE (:itemCode IS NULL OR ti.item_code = :itemCode)
              AND (:startDate IS NULL OR t.trx_date >= :startDate)
              AND (:endDate IS NULL OR t.trx_date <= :endDate)
            GROUP BY DATE_TRUNC('week', t.trx_date)
            ORDER BY week_start
            """, nativeQuery = true)
    List<Object[]> findWeeklySalesTrend(String itemCode, LocalDate startDate, LocalDate endDate);

    // Daily sales trend for a specific product
    @Query(value = """
            SELECT t.trx_date AS day,
                   SUM(ti.qty) AS total_qty,
                   SUM(ti.line_total) AS total_revenue
            FROM transaction_items ti
            JOIN transactions t ON ti.transaction_id = t.id
            WHERE (:itemCode IS NULL OR ti.item_code = :itemCode)
              AND (:startDate IS NULL OR t.trx_date >= :startDate)
              AND (:endDate IS NULL OR t.trx_date <= :endDate)
            GROUP BY t.trx_date
            ORDER BY t.trx_date
            """, nativeQuery = true)
    List<Object[]> findDailySalesTrend(String itemCode, LocalDate startDate, LocalDate endDate);

    // All distinct item codes + names for dropdown
    @Query(value = """
            SELECT DISTINCT ti.item_code, ti.item_name
            FROM transaction_items ti
            ORDER BY ti.item_name
            """, nativeQuery = true)
    List<Object[]> findDistinctProducts();
}
