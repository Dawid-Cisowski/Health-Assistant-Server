package com.healthassistant.evaluation

/**
 * Represents a golden test case for AI hallucination testing.
 *
 * Each test case defines:
 * - A unique name for identification
 * - The question to ask the AI assistant
 * - The expected claim (golden data) that should be reflected in the response
 * - A setup closure that prepares the test data in the database
 */
class GoldenTestCase {

    final String name
    final String question
    final String expectedClaim
    final Closure<Void> setupData

    GoldenTestCase(String name, String question, String expectedClaim, Closure<Void> setupData) {
        this.name = name
        this.question = question
        this.expectedClaim = expectedClaim
        this.setupData = setupData
    }

    @Override
    String toString() {
        return name
    }
}
