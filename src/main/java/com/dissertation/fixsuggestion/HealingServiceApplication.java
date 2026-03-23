package com.dissertation.fixsuggestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Claude Fix Suggestion Service.
 * This service accepts integration test failure reports and microservice source code,
 * then uses CodeSage embeddings and the Anthropic Claude API to generate
 * structured fix suggestions for each failing test.
 */
@SpringBootApplication
public class HealingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HealingServiceApplication.class, args);
    }
}
