package com.dissertation.fixsuggestion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for Claude API HTTP client.
 * API key is injected from environment variable CLAUDE_API_KEY via application.yml.
 */
@Configuration
public class ClaudeApiConfig {
    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.api.url}")
    private String apiUrl;

    @Value("${claude.api.model}")
    private String model;

    @Value("${claude.api.max-tokens}")
    private int maxTokens;

    @Value("${claude.api.version}")
    private String apiVersion;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public String getApiKey()     { return apiKey; }
    public String getApiUrl()     { return apiUrl; }
    public String getModel()      { return model; }
    public int getMaxTokens()     { return maxTokens; }
    public String getApiVersion() { return apiVersion; }
}
