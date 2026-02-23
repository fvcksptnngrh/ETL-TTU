package com.ttu.etl.repository;

import com.ttu.etl.entity.EtlJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EtlJobRepository extends JpaRepository<EtlJob, Long> {

    Optional<EtlJob> findByJobId(String jobId);

    Page<EtlJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
