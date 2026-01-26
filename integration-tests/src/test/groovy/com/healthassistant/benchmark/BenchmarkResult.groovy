package com.healthassistant.benchmark

import groovy.transform.builder.Builder
import groovy.transform.ToString

import java.time.Instant

/**
 * Data class holding benchmark test results for a single test execution.
 * Captures quality, cost, and time metrics.
 */
@Builder(excludes = ['totalTokens'])
@ToString(includeNames = true)
class BenchmarkResult {
    String testId
    String testName
    String model

    // Quality metrics
    boolean passed
    String response
    String errorMessage

    // Cost metrics (tokens)
    long inputTokens
    long outputTokens
    double estimatedCostUsd

    // Time metrics (milliseconds)
    long responseTimeMs
    Long ttftMs  // Time to First Token (null for non-streaming)

    Instant timestamp

    long getTotalTokens() {
        inputTokens + outputTokens
    }

    /**
     * Calculate estimated cost based on Gemini pricing.
     * Gemini 3 Pro: $1.25/1M input, $5.00/1M output
     * Gemini 3 Flash: $0.075/1M input, $0.30/1M output
     */
    static double calculateCost(String model, long inputTokens, long outputTokens) {
        if (model?.contains("flash")) {
            def inputCost = (inputTokens / 1_000_000.0) * 0.075
            def outputCost = (outputTokens / 1_000_000.0) * 0.30
            return inputCost + outputCost
        } else {
            // Pro pricing
            def inputCost = (inputTokens / 1_000_000.0) * 1.25
            def outputCost = (outputTokens / 1_000_000.0) * 5.00
            return inputCost + outputCost
        }
    }
}
