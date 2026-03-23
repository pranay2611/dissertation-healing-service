package com.dissertation.fixsuggestion.service;

import com.dissertation.fixsuggestion.config.ClaudeApiConfig;
import com.dissertation.fixsuggestion.model.response.CodeChange;
import com.dissertation.fixsuggestion.model.response.FailureFix;
import com.dissertation.fixsuggestion.model.internal.FailureCategory;
import com.dissertation.fixsuggestion.model.internal.TestCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Calls the Anthropic Claude API with the constructed prompt
 * and parses the structured JSON response into a FailureFix object.
 *
 * Key behaviors:
 * - Logs prompt sent and raw response at DEBUG level
 * - Strips any accidental markdown fences from Claude's response
 * - Falls back to a LOW-confidence diagnostic fix if JSON parsing fails
 * - Maps confidence string to numeric score for automation tier decisions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeApiService {

    private final ClaudeApiConfig claudeApiConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Sends a prompt to Claude and returns a structured FailureFix.
     *
     * @param prompt       the fully constructed fix prompt
     * @param testCase     the failing test case (for metadata)
     * @param category     the pre-classified failure category
     * @return FailureFix populated with Claude's fix suggestion
     */
    public FailureFix getFixSuggestion(String prompt,
                                       TestCase testCase,
                                       FailureCategory category) {
        log.debug("Sending prompt to Claude for test: {}", testCase.getName());
        log.debug("Prompt content:\n{}", prompt);

        try {
            String rawResponse = callClaudeApi(prompt, testCase.getName());
            log.debug("Raw Claude response:\n{}", rawResponse);
            return parseResponse(rawResponse, testCase, category);
        } catch (Exception e) {
            log.error("Claude API call failed for test '{}': {}", testCase.getName(), e.getMessage());
            return buildFallbackFix(testCase, category, e.getMessage());
        }
    }

    /**
     * Makes the HTTP POST request to the Anthropic Messages API.
     * Logs the full prompt to the console (INFO) and saves it to a temp file
     * so the healing service can read it for audit and replay purposes.
     *
     * Temp file location: /tmp/claude-prompts/prompt-{testName}-{timestamp}.txt
     *
     * @param prompt   the user prompt string
     * @param testName the test case name (used for temp file naming)
     * @return raw response text from Claude
     */
    private String callClaudeApi(String prompt, String testName) {
        // ── Console log: print full prompt so it's visible during curl execution ──
        log.info("==========================================================");
        log.info("CLAUDE PROMPT FOR TEST: {}", testName);
        log.info("==========================================================");
        log.info("\n{}", prompt);
        log.info("==========================================================");

        // ── Save prompt to temp file for healing service consumption ──
        savePromptToTempFile(prompt, testName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", claudeApiConfig.getApiKey());
        headers.set("anthropic-version", claudeApiConfig.getApiVersion());

        Map<String, Object> body = Map.of(
                "model",      claudeApiConfig.getModel(),
                "max_tokens", claudeApiConfig.getMaxTokens(),
                "messages",   List.of(Map.of("role", "user", "content", prompt))
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        log.info("Calling Claude API [model={}] for test: {}", claudeApiConfig.getModel(), testName);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                claudeApiConfig.getApiUrl(), request, Map.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new RuntimeException("Claude API returned: " + response.getStatusCode());
        }

        // Extract text from content[0].text
        List<Map<String, Object>> content = (List<Map<String, Object>>)
                response.getBody().get("content");

        String rawResponse = (String) content.get(0).get("text");

        log.info("----------------------------------------------------------");
        log.info("CLAUDE RESPONSE FOR TEST: {}", testName);
        log.info("----------------------------------------------------------");
        log.info("\n{}", rawResponse);
        log.info("----------------------------------------------------------");

        return rawResponse;
    }

    /**
     * Saves the Claude prompt to a temp file under /tmp/claude-prompts/.
     * Files are named: prompt-{sanitizedTestName}-{epochMillis}.txt
     *
     * The healing service reads these files to:
     * 1. Audit what context was sent for each failure
     * 2. Replay prompts without re-running the full pipeline
     * 3. Compare prompt evolution across runs
     *
     * @param prompt   the full prompt string to persist
     * @param testName the test case name for file identification
     */
    private void savePromptToTempFile(String prompt, String testName) {
        try {
            java.nio.file.Path tempDir = java.nio.file.Paths.get("/tmp/claude-prompts");
            java.nio.file.Files.createDirectories(tempDir);

            // Sanitize test name for safe filename (replace brackets, commas, spaces)
            String safeName = testName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            String filename = String.format("prompt-%s-%d.txt", safeName, System.currentTimeMillis());
            java.nio.file.Path promptFile = tempDir.resolve(filename);

            String content = String.join("\n",
                    "# Claude Prompt - Healing Service Audit File",
                    "# Test: " + testName,
                    "# Generated: " + java.time.Instant.now(),
                    "# Model: " + claudeApiConfig.getModel(),
                    "#",
                    "# This file is consumed by the healing service for prompt audit and replay.",
                    "# Location: /tmp/claude-prompts/",
                    "# ============================================================",
                    "",
                    prompt
            );

            java.nio.file.Files.writeString(promptFile, content);
            log.info("Prompt saved to temp file: {}", promptFile.toAbsolutePath());

        } catch (Exception e) {
            // Non-fatal: log warning but do not interrupt the fix pipeline
            log.warn("Failed to save prompt to temp file for test '{}': {}", testName, e.getMessage());
        }
    }

    /**
     * Parses Claude's JSON response into a FailureFix object.
     * Strips markdown code fences if accidentally included by Claude.
     *
     * @param rawResponse  the raw text returned by Claude
     * @param testCase     the originating test case
     * @param category     the pre-classified failure category
     * @return populated FailureFix
     */
    private FailureFix parseResponse(String rawResponse,
                                     TestCase testCase,
                                     FailureCategory category) throws Exception {
        // Strip potential markdown fences
        String json = rawResponse
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        JsonNode root = objectMapper.readTree(json);

        String confidence      = root.path("confidence").asText("LOW");
        double confidenceScore = root.path("confidenceScore").asDouble(0.5);
        String fixLocation     = root.path("fixLocation").asText("UNKNOWN");
        String rootCause       = root.path("rootCause").asText("");
        String explanation     = root.path("explanation").asText("");

        List<CodeChange> codeChanges = new ArrayList<>();
        for (JsonNode change : root.path("codeChanges")) {
            String perChangeLocation = change.path("fixLocation").asText(fixLocation);
            String perChangeConfidence = change.path("confidence").asText(confidence);
            double perChangeConfidenceScore = change.path("confidenceScore").asDouble(confidenceScore);
            codeChanges.add(CodeChange.builder()
                    .filePath(change.path("filePath").asText())
                    .fixLocation(perChangeLocation)
                    .changeDescription(change.path("changeDescription").asText())
                    .suggestedCode(change.path("suggestedCode").asText())
                    .confidence(perChangeConfidence)
                    .confidenceScore(perChangeConfidenceScore)
                    .build());
        }

        return FailureFix.builder()
                .testName(testCase.getName())
                .failureCategory(category)
                .rootCause(rootCause)
                .explanation(explanation)
                .fixLocation(fixLocation)
                .codeChanges(codeChanges)
                .confidence(confidence)
                .confidenceScore(confidenceScore)
                .automationEligible(confidenceScore >= 0.85)
                .build();
    }

    /**
     * Builds a fallback FailureFix when the Claude API call or JSON parsing fails.
     * Returns LOW confidence to ensure no automated action is taken.
     *
     * @param testCase  the failing test case
     * @param category  the pre-classified category
     * @param errorMsg  the error message from the exception
     * @return safe fallback FailureFix
     */
    private FailureFix buildFallbackFix(TestCase testCase,
                                        FailureCategory category,
                                        String errorMsg) {
        return FailureFix.builder()
                .testName(testCase.getName())
                .failureCategory(category)
                .rootCause("Claude API unavailable or response parsing failed: " + errorMsg)
                .explanation("Manual investigation required. Could not generate automated fix.")
                .fixLocation("UNKNOWN")
                .codeChanges(List.of())
                .confidence("LOW")
                .confidenceScore(0.0)
                .automationEligible(false)
                .build();
    }
}