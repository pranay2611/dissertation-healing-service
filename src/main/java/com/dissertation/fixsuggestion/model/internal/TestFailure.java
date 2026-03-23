package com.dissertation.fixsuggestion.model.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Represents the failure details from a TestNG JSON report entry.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestFailure {
    private String message;
    private String type;
    private String stackTrace;
}
