package com.dissertation.fixsuggestion.model.response;

import com.dissertation.fixsuggestion.model.internal.FailureCategory;
import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * The fix suggestion produced for a single failing test case.
 * Combines the categorization result with Claude's structured fix output.
 */
@Data
@Builder
public class FailureFix {
    private String testName;
    private FailureCategory failureCategory;
    private String rootCause;
    private String explanation;
    private String fixLocation;         // TEST | SERVICE | BOTH
    private List<CodeChange> codeChanges;
    private String confidence;          // HIGH | MEDIUM | LOW
    private double confidenceScore;     // 0.0 - 1.0
    private boolean automationEligible; // true if confidence > 0.85
}