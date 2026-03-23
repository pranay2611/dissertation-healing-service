package com.dissertation.fixsuggestion.model.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

/**
 * Represents a test suite section in the TestNG JSON report.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestSuite {
    private String name;
    private int tests;
    private int failures;
    private int errors;
    private int skipped;
    private double time;
    private List<TestCase> testCases;
}