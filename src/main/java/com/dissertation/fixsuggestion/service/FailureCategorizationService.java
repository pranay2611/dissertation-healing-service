package com.dissertation.fixsuggestion.service;

import com.dissertation.fixsuggestion.model.internal.FailureCategory;
import com.dissertation.fixsuggestion.model.internal.TestCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Categorizes each failed test case into a FailureCategory
 * by inspecting HTTP response codes and assertion messages in the failure output.
 *
 * Categories are used to:
 * 1. Provide structured metadata to Claude in the fix prompt
 * 2. Enable downstream automation decisions (e.g., auto-merge eligibility)
 * 3. Support thesis evaluation of classification accuracy
 */
@Slf4j
@Service
public class FailureCategorizationService {

    /**
     * Determines the failure category for a given test case.
     *
     * @param testCase the failed test case to categorize
     * @return the most appropriate FailureCategory
     */
    public FailureCategory categorize(TestCase testCase) {
        if (testCase.getFailure() == null || testCase.getFailure().getMessage() == null) {
            return FailureCategory.UNKNOWN;
        }

        String message = testCase.getFailure().getMessage().toLowerCase();
        FailureCategory category = detectFromMessage(message);

        log.debug("Test '{}' categorized as: {}", testCase.getName(), category);
        return category;
    }

    /**
     * Applies rule-based matching on the failure message to determine category.
     * Rules are ordered from most specific to most general.
     *
     * @param message lowercased failure message string
     * @return matched FailureCategory
     */
    private FailureCategory detectFromMessage(String message) {
        // 500 response with JSON parse error = service didn't handle invalid input gracefully
        if (message.contains("500") && (message.contains("json parse error") ||
                message.contains("cannot deserialize") || message.contains("parse error"))) {
            return FailureCategory.INVALID_INPUT_HANDLING;
        }

        // 404 response = test called the wrong endpoint path
        if (message.contains("404") || message.contains("not found")) {
            return FailureCategory.WRONG_ENDPOINT;
        }

        // 400 response with wrong contract = request body mismatch
        if (message.contains("400") && message.contains("bad request")) {
            return FailureCategory.CONTRACT_VIOLATION;
        }

        // Service returned correct code but test expected wrong code
        if (message.contains("expected [201] but found [200]") ||
                message.contains("expected [200] but found [201]") ||
                message.contains("wrong assertion") || message.contains("not 201")) {
            return FailureCategory.WRONG_ASSERTION;
        }

        // General 500 error
        if (message.contains("500") || message.contains("internal server error")) {
            return FailureCategory.INTERNAL_ERROR;
        }

        return FailureCategory.UNKNOWN;
    }
}