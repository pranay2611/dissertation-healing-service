package com.dissertation.fixsuggestion.model.internal;

/**
 * Enumeration of integration test failure categories.
 * Each category maps to a distinct failure signal pattern detected
 * from the test assertion message and HTTP response status.
 */
public enum FailureCategory {
    /** Test expected wrong HTTP status code; service response is actually correct */
    WRONG_ASSERTION,

    /** Test called an incorrect URL path; service returned 404 */
    WRONG_ENDPOINT,

    /** Request body did not conform to the API contract; service returned 400 */
    CONTRACT_VIOLATION,

    /** Service received invalid input but returned 500 instead of 400 */
    INVALID_INPUT_HANDLING,

    /** Unhandled server-side exception unrelated to client input */
    INTERNAL_ERROR,

    /** Failure could not be mapped to a known category */
    UNKNOWN
}
