package com.example.tdd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    /** Used by OrderEnrichmentService for parallel I/O on virtual threads. */
    @Bean
    public Executor enrichmentExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
