package com.dissertation.fixsuggestion.service;

import com.dissertation.fixsuggestion.model.internal.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestResultParserServiceTest {

    private final TestResultParserService service = new TestResultParserService();

    @Test
    void shouldExtractOnlyFailedTests() {
        TestCase passed = new TestCase();
        passed.setStatus("PASSED");
        passed.setName("passingTest");

        TestCase failed = new TestCase();
        failed.setStatus("FAILED");
        failed.setName("failingTest");
        TestFailure failure = new TestFailure();
        failure.setMessage("expected [200] but found [404]");
        failure.setStackTrace("line1\nline2\nline3\nline4\nline5\nline6\nline7");
        failed.setFailure(failure);

        TestSuite suite = new TestSuite();
        suite.setTestCases(List.of(passed, failed));

        TestResultReport report = new TestResultReport();
        report.setSuites(List.of(suite));

        List<TestCase> failures = service.extractFailures(report);

        assertEquals(1, failures.size());
        assertEquals("failingTest", failures.get(0).getName());
        // Stack trace should be trimmed to 5 frames
        assertEquals(5, failures.get(0).getFailure().getStackTrace().split("\n").length);
    }
}