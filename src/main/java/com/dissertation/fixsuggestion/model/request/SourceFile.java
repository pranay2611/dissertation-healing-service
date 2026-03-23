package com.dissertation.fixsuggestion.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Represents a single source file from the microservice being analyzed.
 * The full file content is included to provide Claude with maximum context.
 */
@Data
public class SourceFile {

    @NotBlank(message = "filePath is required")
    private String filePath;

    @NotBlank(message = "content is required")
    private String content;
}