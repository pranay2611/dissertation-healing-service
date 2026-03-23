package com.dissertation.fixsuggestion.service;

import com.dissertation.fixsuggestion.model.internal.TestCase;
import com.dissertation.fixsuggestion.model.internal.TestResultReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Parses the TestNG JSON report and extracts only the FAILED test cases.
 * Also trims stack traces to the top N frames to reduce Claude prompt token usage.
 */
@Slf4j
@Service
public class TestResultParserService {

    private static final int MAX_STACK_FRAMES = 5;

    /**
     * Extracts all failed test cases from all suites in the report.
     *
     * @param report the parsed TestNG JSON report
     * @return list of failed TestCase objects with trimmed stack traces
     */
    public List<TestCase> extractFailures(TestResultReport report) {
        if (report.getSuites() == null) {
            log.warn("No test suites found in report");
            return List.of();
        }

        List<TestCase> failures = report.getSuites().stream()
                .filter(suite -> suite.getTestCases() != null)
                .flatMap(suite -> suite.getTestCases().stream())
                .filter(TestCase::isFailed)
                .collect(Collectors.toList());

        log.debug("Extracted {} failed test cases from report", failures.size());

        // Trim stack traces before returning
        failures.forEach(this::trimStackTrace);
        return failures;
    }

    /**
     * Trims the stack trace of a failed test to MAX_STACK_FRAMES lines.
     * This reduces token consumption when sending context to Claude.
     *
     * @param testCase the failed test case whose stack trace should be trimmed
     */
    private void trimStackTrace(TestCase testCase) {
        if (testCase.getFailure() == null || testCase.getFailure().getStackTrace() == null) {
            return;
        }
        String[] lines = testCase.getFailure().getStackTrace().split("\n");
        String trimmed = Arrays.stream(lines)
                .limit(MAX_STACK_FRAMES)
                .collect(Collectors.joining("\n"));
        testCase.getFailure().setStackTrace(trimmed);
        log.debug("Trimmed stack trace for test: {}", testCase.getName());
    }
}