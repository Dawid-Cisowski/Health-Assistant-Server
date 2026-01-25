package com.healthassistant.benchmark

import groovy.json.JsonBuilder
import groovy.json.JsonOutput

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Generates benchmark reports in various formats.
 * Aggregates results and produces comparison tables.
 */
class BenchmarkReporter {

    private static final List<BenchmarkResult> results = Collections.synchronizedList([])

    static void recordResult(BenchmarkResult result) {
        results.add(result)
    }

    static void clear() {
        results.clear()
    }

    static List<BenchmarkResult> getResults() {
        new ArrayList<>(results)
    }

    /**
     * Print console report with comparison table.
     */
    static void printConsoleReport() {
        if (results.isEmpty()) {
            println "No benchmark results to report."
            return
        }

        def grouped = results.groupBy { it.model }
        def models = grouped.keySet().sort()

        println ""
        println "=" * 90
        println "                         AI BENCHMARK REPORT - ${formatDate(Instant.now())}"
        println "=" * 90
        println ""
        printf "%-30s │ %-15s │ %-6s │ %-12s │ %-8s │ %-7s%n",
                "Test", "Model", "Pass", "Tokens", "Cost", "Time"
        println "-" * 90

        def testIds = results.collect { it.testId }.unique().sort()

        testIds.each { testId ->
            def testResults = results.findAll { it.testId == testId }
            testResults.each { result ->
                def passStr = result.passed ? "  \u2705  " : "  \u274C  "
                def tokensStr = "${result.inputTokens}/${result.outputTokens}"
                def costStr = String.format('$%.4f', result.estimatedCostUsd)
                def timeStr = String.format('%.1fs', result.responseTimeMs / 1000.0)
                def modelShort = result.model?.replace("-preview", "")?.replace("gemini-", "") ?: "unknown"

                printf "%-30s │ %-15s │ %-6s │ %-12s │ %-8s │ %-7s%n",
                        truncate(result.testName, 30), truncate(modelShort, 15),
                        passStr, tokensStr, costStr, timeStr
            }
            if (testResults.size() > 1) {
                println "-" * 90
            }
        }

        println "=" * 90
        println ""

        // Print totals per model
        println "TOTALS BY MODEL:"
        println "-" * 60
        models.each { model ->
            def modelResults = grouped[model]
            def passed = modelResults.count { it.passed }
            def total = modelResults.size()
            def totalInputTokens = modelResults.sum { it.inputTokens } ?: 0
            def totalOutputTokens = modelResults.sum { it.outputTokens } ?: 0
            def totalCost = modelResults.sum { it.estimatedCostUsd } ?: 0.0
            def avgTime = modelResults.sum { it.responseTimeMs } / modelResults.size()
            def modelShort = model?.replace("-preview", "")?.replace("gemini-", "") ?: "unknown"

            printf "%-20s: %d/%d passed │ Tokens: %d/%d │ Cost: \$%.4f │ Avg Time: %.1fs%n",
                    modelShort, passed, total, totalInputTokens, totalOutputTokens, totalCost, avgTime / 1000.0
        }

        println "=" * 90
        println ""
    }

    /**
     * Generate JSON report file.
     */
    static void writeJsonReport(Path outputPath) {
        def grouped = results.groupBy { it.model }

        def report = [
                timestamp : Instant.now().toString(),
                summary   : [
                        totalTests    : results.size(),
                        passedTests   : results.count { it.passed },
                        failedTests   : results.count { !it.passed },
                        totalTokens   : results.sum { it.totalTokens } ?: 0,
                        totalCostUsd  : results.sum { it.estimatedCostUsd } ?: 0.0,
                        avgResponseMs : results.isEmpty() ? 0 : results.sum { it.responseTimeMs } / results.size()
                ],
                byModel   : grouped.collectEntries { model, modelResults ->
                    [model, [
                            passed       : modelResults.count { it.passed },
                            failed       : modelResults.count { !it.passed },
                            inputTokens  : modelResults.sum { it.inputTokens } ?: 0,
                            outputTokens : modelResults.sum { it.outputTokens } ?: 0,
                            totalCostUsd : modelResults.sum { it.estimatedCostUsd } ?: 0.0,
                            avgResponseMs: modelResults.sum { it.responseTimeMs } / modelResults.size()
                    ]]
                },
                tests     : results.collect { result ->
                    [
                            testId          : result.testId,
                            testName        : result.testName,
                            model           : result.model,
                            passed          : result.passed,
                            inputTokens     : result.inputTokens,
                            outputTokens    : result.outputTokens,
                            estimatedCostUsd: result.estimatedCostUsd,
                            responseTimeMs  : result.responseTimeMs,
                            ttftMs          : result.ttftMs,
                            timestamp       : result.timestamp?.toString(),
                            errorMessage    : result.errorMessage
                    ]
                }
        ]

        Files.createDirectories(outputPath.parent)
        outputPath.toFile().text = JsonOutput.prettyPrint(new JsonBuilder(report).toString())
        println "JSON report written to: ${outputPath}"
    }

    private static String formatDate(Instant instant) {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(instant)
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return ""
        str.length() > maxLen ? str.substring(0, maxLen - 2) + ".." : str
    }
}
