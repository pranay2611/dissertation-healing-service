package com.dissertation.fixsuggestion.model.request;

import com.dissertation.fixsuggestion.model.internal.TestResultReport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Inbound request payload for the fix suggestion endpoint.
 * Contains the full test result report and the relevant microservice source files.
 */
@Data
public class FixSuggestionRequest {

    @NotNull(message = "testResultJson is required")
    private TestResultReport testResultJson;

    @Valid
    private MicroserviceContext microserviceContext;

    @Data
    public static class MicroserviceContext {
        // Legacy single-service fields (kept for backward compatibility)
        private List<SourceFile> sourceFiles;
        private String buildTool = "maven";
        private String springBootVersion = "3.x";

        // Preferred multi-service representation
        @Valid
        private List<ServiceInfo> services;

        /**
         * Returns all source files across all services.
         * Falls back to legacy single-service sourceFiles when services[] is absent.
         */
        public List<SourceFile> getAllSourceFiles() {
            List<SourceFile> files = new ArrayList<>();
            if (services != null && !services.isEmpty()) {
                for (ServiceInfo svc : services) {
                    if (svc.getSourceFiles() != null) {
                        files.addAll(svc.getSourceFiles());
                    }
                }
                return files;
            }
            if (sourceFiles != null) {
                files.addAll(sourceFiles);
            }
            return files;
        }

        /**
         * Returns a human-readable service label for logs and prompt headers.
         */
        public String getServiceLabel() {
            if (services != null && !services.isEmpty()) {
                if (services.size() == 1) {
                    return services.get(0).getServiceName();
                }
                return "multiple-services(" + services.size() + ")";
            }
            return "unknown";
        }
    }

    @Data
    public static class ServiceInfo {
        @NotBlank
        private String serviceName;
        private String buildTool = "maven";
        private String springBootVersion = "3.x";
        @Valid
        private List<SourceFile> sourceFiles;
    }
}