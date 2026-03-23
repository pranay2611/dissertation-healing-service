package com.dissertation.fixsuggestion.model.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Represents a single test case entry from the TestNG JSON report.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestCase {
    private String name;
    private String className;
    private double durationSeconds;
    private String status;
    private TestFailure failure;

    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status);
    }
}
