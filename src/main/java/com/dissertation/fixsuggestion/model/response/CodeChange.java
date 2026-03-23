package com.dissertation.fixsuggestion.model.response;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a specific code change recommended by Claude
 * for a given failing test case.
 */
@Data
@Builder
public class CodeChange {
    private String filePath;
    /** TEST | SERVICE (or BOTH when suggestion affects both). */
    private String fixLocation;
    private String changeDescription;
    private String suggestedCode;
    /** HIGH | MEDIUM | LOW for this specific suggestion. */
    private String confidence;
    /** 0.0 - 1.0 confidence score for this specific suggestion. */
    private Double confidenceScore;
}