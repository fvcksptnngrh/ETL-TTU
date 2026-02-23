package com.ttu.etl.repository;

import com.ttu.etl.entity.ErrorLog;
import com.ttu.etl.entity.EtlJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {

    List<ErrorLog> findByEtlJobOrderByRowNumberAsc(EtlJob etlJob);

    long countByEtlJob(EtlJob etlJob);

    long countByEtlJobId(Long etlJobId);

    long countByEtlJobIdAndErrorTypeNot(Long etlJobId, ErrorLog.ErrorType errorType);
}
