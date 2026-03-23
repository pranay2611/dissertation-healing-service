package com.dissertation.fixsuggestion.model.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Top-level model representing the entire TestNG JSON report.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestResultReport {
    private String generatedAt;
    private Map<String, Object> summary;
    private List<TestSuite> suites;
}