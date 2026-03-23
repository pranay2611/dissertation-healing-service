package com.dissertation.fixsuggestion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for CodeSage semantic embedding model.
 * Uses DJL (Deep Java Library) to load a HuggingFace code embedding model
 * compatible with CodeSage's architecture.
 */
@Configuration
public class CodeSageConfig {
    @Value("${codesage.model.name}")
    private String modelName;

    @Value("${codesage.model.max-sequence-length}")
    private int maxSequenceLength;

    @Value("${codesage.model.similarity-threshold}")
    private double similarityThreshold;

    public String getModelName()          { return modelName; }
    public int getMaxSequenceLength()     { return maxSequenceLength; }
    public double getSimilarityThreshold(){ return similarityThreshold; }
}
