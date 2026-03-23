package com.dissertation.fixsuggestion.model.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

/**
 * Top-level response returned by POST /api/fix-suggestions.
 */
@Data
@Builder
public class FixSuggestionResponse {
    private Instant analysedAt;
    private int totalFailed;
    private int totalProcessed;
    private List<FailureFix> fixes;
}