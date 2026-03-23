package com.dissertation.fixsuggestion.service;

import com.dissertation.fixsuggestion.model.internal.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FailureCategorizationServiceTest {

    private final FailureCategorizationService service = new FailureCategorizationService();

    private TestCase testCaseWithMessage(String message) {
        TestCase tc = new TestCase();
        tc.setStatus("FAILED");
        TestFailure f = new TestFailure();
        f.setMessage(message);
        tc.setFailure(f);
        return tc;
    }

    @Test void detectsWrongEndpoint() {
        assertEquals(FailureCategory.WRONG_ENDPOINT,
                service.categorize(testCaseWithMessage(
                        "expected [200] but found [404]. Response: Not Found /api/users/register")));
    }

    @Test void detectsInvalidInputHandling() {
        assertEquals(FailureCategory.INVALID_INPUT_HANDLING,
                service.categorize(testCaseWithMessage(
                        "expected [201] but found [500]. JSON parse error: Cannot deserialize")));
    }

    @Test void detectsContractViolation() {
        assertEquals(FailureCategory.CONTRACT_VIOLATION,
                service.categorize(testCaseWithMessage(
                        "expected [200] but found [400]. Bad Request /api/auth/register")));
    }

    @Test void detectsWrongAssertion() {
        assertEquals(FailureCategory.WRONG_ASSERTION,
                service.categorize(testCaseWithMessage(
                        "This test is intended to fail: registration returns 200, not 201. expected [201] but found [200]")));
    }
}