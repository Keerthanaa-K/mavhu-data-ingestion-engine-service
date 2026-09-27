package com.mavhu.ingestion.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables @Async (processor trigger and file processing); runs on Spring Boot's
 * auto-configured task executor, tunable via spring.task.execution.*.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
