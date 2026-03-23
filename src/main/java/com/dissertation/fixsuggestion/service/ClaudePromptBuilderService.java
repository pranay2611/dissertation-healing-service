package com.dissertation.fixsuggestion.service;

import com.dissertation.fixsuggestion.model.internal.FailureCategory;
import com.dissertation.fixsuggestion.model.internal.TestCase;
import com.dissertation.fixsuggestion.model.request.FixSuggestionRequest;
import com.dissertation.fixsuggestion.model.request.SourceFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Constructs the structured prompt sent to the Claude API for each failing test case.
 *
 * The prompt is built in four sections:
 * 1. FAILURE CONTEXT   — test name, category, assertion error, trimmed stack trace
 * 2. SOURCE CODE       — relevant files (filtered by CodeSage relevance ranking)
 * 3. TASK              — explicit instructions for Claude's analysis
 * 4. OUTPUT FORMAT     — JSON schema Claude must follow in its response
 *
 * This structured format is central to the thesis's fix quality evaluation —
 * prompt structure is a controlled variable across experiments.
 */
@Slf4j
@Service
public class ClaudePromptBuilderService {

    /**
     * Builds the full prompt for a single failing test case.
     *
     * @param testCase       the failing test case
     * @param category       the pre-classified failure category
     * @param relevantFiles  source files ranked relevant by CodeSage
     * @param context        the full microservice context from the request
     * @return formatted prompt string ready to send to Claude
     */
    public String buildPrompt(TestCase testCase,
                              FailureCategory category,
                              List<String> relevantFiles,
                              FixSuggestionRequest.MicroserviceContext context) {

        StringBuilder prompt = new StringBuilder();

        // Required outer prompt structure for the thesis experiments.
        prompt.append("<role>You are a software engineer specializing in Spring Boot and code sage</role>\n\n");
        prompt.append("<task>\n");
        appendTask(prompt);
        prompt.append("</task>\n\n");
        prompt.append("<instructions>\n");
        appendFailureContext(prompt, testCase, category);
        appendSourceCode(prompt, relevantFiles, context);
        appendOutputFormat(prompt);
        prompt.append("\n</instructions>");

        String built = prompt.toString();
        log.debug("Built prompt for test '{}' ({} chars)", testCase.getName(), built.length());
        return built;
    }

    private void appendFailureContext(StringBuilder sb,
                                      TestCase testCase,
                                      FailureCategory category) {
        sb.append("<failureContext>\n");
        sb.append("Test Name: ").append(testCase.getName()).append("\n");
        sb.append("Failure Category: ").append(category.name()).append("\n");

        if (testCase.getFailure() != null) {
            sb.append("Assertion Error: ").append(testCase.getFailure().getMessage()).append("\n");
            sb.append("Exception Type: ").append(testCase.getFailure().getType()).append("\n");
            if (testCase.getFailure().getStackTrace() != null) {
                sb.append("Stack Trace (top frames):\n")
                        .append(testCase.getFailure().getStackTrace()).append("\n");
            }
        }
        sb.append("</failureContext>\n\n");
    }

    private void appendSourceCode(StringBuilder sb,
                                  List<String> relevantFilePaths,
                                  FixSuggestionRequest.MicroserviceContext context) {
        sb.append("<sourceCode>\n");

        List<SourceFile> files = context != null ? context.getAllSourceFiles() : List.of();
        if (files == null || files.isEmpty() || relevantFilePaths.isEmpty()) {
            sb.append("No source files provided.\n");
            sb.append("</sourceCode>\n\n");
            return;
        }

        files.stream()
                // Always include test sources when present, plus relevance-ranked files.
                .filter(f -> relevantFilePaths.contains(f.getFilePath()) || isTestSourceFile(f.getFilePath()))
                .forEach(f -> {
                    sb.append("<file path=\"").append(f.getFilePath()).append("\">\n");
                    sb.append(f.getContent()).append("\n\n");
                    sb.append("</file>\n");
                });
        sb.append("</sourceCode>\n\n");
    }

    private boolean isTestSourceFile(String path) {
        if (path == null) {
            return false;
        }
        return path.contains("/src/test/") || path.startsWith("src/test/");
    }

    private void appendTask(StringBuilder sb) {
        sb.append("Debug a Spring Boot microservice integration test failure.\n");
        sb.append("Identify the root cause, then decide whether the fix belongs to TEST, SERVICE, or BOTH.\n");
        sb.append("Provide MULTIPLE fix suggestions when applicable, covering both TEST and SERVICE locations.\n");
        sb.append("Each suggestion must include its own confidence and confidenceScore.\n");
    }

    private void appendOutputFormat(StringBuilder sb) {
        sb.append("<outputFormat>\n");
        sb.append("Respond ONLY with a valid JSON object (no preamble, no markdown fences).\n");
        sb.append("{\n");
        sb.append("  \"rootCause\": \"string\",\n");
        sb.append("  \"fixLocation\": \"TEST | SERVICE | BOTH\",\n");
        sb.append("  \"explanation\": \"string\",\n");
        sb.append("  \"codeChanges\": [\n");
        sb.append("    {\n");
        sb.append("      \"filePath\": \"string\",\n");
        sb.append("      \"fixLocation\": \"TEST | SERVICE | BOTH\",\n");
        sb.append("      \"changeDescription\": \"string\",\n");
        sb.append("      \"suggestedCode\": \"string\",\n");
        sb.append("      \"confidence\": \"HIGH | MEDIUM | LOW\",\n");
        sb.append("      \"confidenceScore\": 0.0\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"confidence\": \"HIGH | MEDIUM | LOW\",\n");
        sb.append("  \"confidenceScore\": 0.0\n");
        sb.append("}\n");
        sb.append("Return at least one TEST suggestion and one SERVICE suggestion whenever both are plausible.\n");
        sb.append("</outputFormat>\n");
    }
}