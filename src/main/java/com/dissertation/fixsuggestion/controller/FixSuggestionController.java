package com.dissertation.fixsuggestion.controller;

import com.dissertation.fixsuggestion.model.internal.FailureCategory;
import com.dissertation.fixsuggestion.model.internal.TestCase;
import com.dissertation.fixsuggestion.model.request.FixSuggestionRequest;
import com.dissertation.fixsuggestion.model.request.SourceFile;
import com.dissertation.fixsuggestion.model.response.FailureFix;
import com.dissertation.fixsuggestion.model.response.FixSuggestionResponse;
import com.dissertation.fixsuggestion.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * REST controller exposing the fix suggestion endpoint.
 *
 * POST /api/fix-suggestions
 * Accepts a TestNG JSON report + microservice source files.
 * Returns structured Claude fix suggestions per failing test.
 *
 * Pipeline: Parse → Embed → Categorize → Build Prompt → Call Claude → Respond
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FixSuggestionController {

    private final TestResultParserService parserService;
    private final FailureCategorizationService categorizationService;
    private final CodeSageEmbeddingService embeddingService;
    private final ClaudePromptBuilderService promptBuilderService;
    private final ClaudeApiService claudeApiService;

    /**
     * Main endpoint: accepts test results and source code, returns fix suggestions.
     */
    @PostMapping("/fix-suggestions")
    public ResponseEntity<FixSuggestionResponse> suggestFixes(
            @Valid @RequestBody FixSuggestionRequest request) {

        log.info("Received fix suggestion request for service: {}",
                request.getMicroserviceContext() != null
                        ? request.getMicroserviceContext().getServiceLabel() : "unknown");

        // Step 1: Extract failed test cases
        List<TestCase> failures = parserService.extractFailures(request.getTestResultJson());
        log.info("Processing {} failed test cases", failures.size());

        // Step 2: Generate CodeSage embeddings for source files
        List<SourceFile> sourceFiles = request.getMicroserviceContext() != null
                ? request.getMicroserviceContext().getAllSourceFiles() : List.of();

        Map<String, float[]> embeddings = embeddingService.generateEmbeddings(
                sourceFiles != null ? sourceFiles : List.of());

        // Step 3: Process each failure through the fix pipeline
        List<FailureFix> fixes = new ArrayList<>();

        for (TestCase testCase : failures) {
            log.info("Processing failure: {}", testCase.getName());

            // 3a. Categorize the failure
            FailureCategory category = categorizationService.categorize(testCase);

            // 3b. Find relevant source files via CodeSage similarity
            String errorContext = testCase.getFailure() != null
                    ? testCase.getFailure().getMessage() : testCase.getName();

            List<String> relevantFilePaths = embeddingService.findRelevantFiles(
                    errorContext, embeddings, 3);

            // If no files matched threshold, include all files (small set)
            if (relevantFilePaths.isEmpty() && sourceFiles != null) {
                relevantFilePaths = sourceFiles.stream()
                        .map(SourceFile::getFilePath)
                        .toList();
            }

            // 3c. Build prompt
            String prompt = promptBuilderService.buildPrompt(
                    testCase, category, relevantFilePaths, request.getMicroserviceContext());

            // 3d. Call Claude API
            FailureFix fix = claudeApiService.getFixSuggestion(prompt, testCase, category);
            fixes.add(fix);
        }

        FixSuggestionResponse response = FixSuggestionResponse.builder()
                .analysedAt(Instant.now())
                .totalFailed(failures.size())
                .totalProcessed(fixes.size())
                .fixes(fixes)
                .build();

        log.info("Fix suggestion complete: {}/{} processed", fixes.size(), failures.size());
        return ResponseEntity.ok(response);
    }
}