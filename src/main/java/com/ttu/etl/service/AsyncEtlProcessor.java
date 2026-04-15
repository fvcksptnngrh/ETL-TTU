package com.ttu.etl.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async entry point for ETL processing. Intentionally thin: delegates the
 * transactional work to {@link EtlPipelineRunner} on a separate bean so the
 * @Async and @Transactional proxies don't collide via self-invocation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncEtlProcessor {

    private final EtlPipelineRunner pipelineRunner;

    @Async
    public void processFile(Long etlJobId, String filePath) {
        try {
            pipelineRunner.run(etlJobId, filePath);
        } catch (Exception e) {
            pipelineRunner.markFailed(etlJobId, e);
        }
    }
}
