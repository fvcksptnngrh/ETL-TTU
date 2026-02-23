package com.ttu.etl.repository;

import com.ttu.etl.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByItemCode(String itemCode);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.etlJob")
    List<Product> findAllWithEtlJob();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.etlJob WHERE p.itemCode = :itemCode")
    Optional<Product> findByItemCodeWithEtlJob(String itemCode);

    boolean existsByItemCode(String itemCode);

    long countByEtlJobId(Long etlJobId);

    List<Product> findByEtlJobId(Long etlJobId);
}
