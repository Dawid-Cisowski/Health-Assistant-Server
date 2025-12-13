package com.healthassistant

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.scheduling.annotation.AsyncConfigurer

import java.util.concurrent.Executor

/**
 * Test configuration that makes @Async methods run synchronously.
 * This allows integration tests to verify results immediately after
 * triggering async operations like @ApplicationModuleListener event handlers.
 */
@TestConfiguration
class TestAsyncConfiguration implements AsyncConfigurer {

    @Bean
    @Primary
    Executor taskExecutor() {
        // SyncTaskExecutor runs tasks in the calling thread, making them synchronous
        return new SyncTaskExecutor()
    }

    @Override
    Executor getAsyncExecutor() {
        return new SyncTaskExecutor()
    }
}
