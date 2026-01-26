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

    /**
     * Generate HTML report with styled table.
     */
    static void writeHtmlReport(Path outputPath) {
        def grouped = results.groupBy { it.model }
        def models = grouped.keySet().sort()
        def testIds = results.collect { it.testId }.unique().sort()

        def totalPassed = results.count { it.passed }
        def totalFailed = results.count { !it.passed }
        def totalTokens = results.sum { it.totalTokens } ?: 0
        def totalCost = results.sum { it.estimatedCostUsd } ?: 0.0
        def avgTime = results.isEmpty() ? 0 : results.sum { it.responseTimeMs } / results.size()

        def html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AI Benchmark Report - ${formatDate(Instant.now())}</title>
    <style>
        :root {
            --bg-primary: #0d1117;
            --bg-secondary: #161b22;
            --bg-tertiary: #21262d;
            --text-primary: #c9d1d9;
            --text-secondary: #8b949e;
            --border-color: #30363d;
            --accent-green: #3fb950;
            --accent-red: #f85149;
            --accent-blue: #58a6ff;
            --accent-purple: #a371f7;
            --accent-orange: #d29922;
        }

        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', Helvetica, Arial, sans-serif;
            background: var(--bg-primary);
            color: var(--text-primary);
            line-height: 1.5;
            padding: 2rem;
        }

        .container {
            max-width: 1400px;
            margin: 0 auto;
        }

        h1 {
            font-size: 2rem;
            font-weight: 600;
            margin-bottom: 0.5rem;
            background: linear-gradient(90deg, var(--accent-blue), var(--accent-purple));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }

        .subtitle {
            color: var(--text-secondary);
            margin-bottom: 2rem;
        }

        .summary-cards {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 1rem;
            margin-bottom: 2rem;
        }

        .card {
            background: var(--bg-secondary);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 1.25rem;
        }

        .card-label {
            font-size: 0.75rem;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--text-secondary);
            margin-bottom: 0.5rem;
        }

        .card-value {
            font-size: 1.75rem;
            font-weight: 600;
        }

        .card-value.success { color: var(--accent-green); }
        .card-value.error { color: var(--accent-red); }
        .card-value.info { color: var(--accent-blue); }
        .card-value.warning { color: var(--accent-orange); }

        .section {
            background: var(--bg-secondary);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            margin-bottom: 2rem;
            overflow: hidden;
        }

        .section-header {
            background: var(--bg-tertiary);
            padding: 1rem 1.25rem;
            border-bottom: 1px solid var(--border-color);
            font-weight: 600;
        }

        table {
            width: 100%;
            border-collapse: collapse;
        }

        th, td {
            padding: 0.75rem 1rem;
            text-align: left;
            border-bottom: 1px solid var(--border-color);
        }

        th {
            background: var(--bg-tertiary);
            font-weight: 600;
            font-size: 0.75rem;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--text-secondary);
        }

        tr:hover {
            background: var(--bg-tertiary);
        }

        tr:last-child td {
            border-bottom: none;
        }

        .status {
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0.25rem 0.75rem;
            border-radius: 9999px;
            font-size: 0.875rem;
            font-weight: 500;
        }

        .status.pass {
            background: rgba(63, 185, 80, 0.15);
            color: var(--accent-green);
        }

        .status.fail {
            background: rgba(248, 81, 73, 0.15);
            color: var(--accent-red);
        }

        .model-badge {
            display: inline-block;
            padding: 0.25rem 0.5rem;
            border-radius: 4px;
            font-size: 0.75rem;
            font-weight: 500;
            background: var(--bg-tertiary);
            border: 1px solid var(--border-color);
        }

        .model-badge.flash {
            border-color: var(--accent-blue);
            color: var(--accent-blue);
        }

        .model-badge.pro {
            border-color: var(--accent-purple);
            color: var(--accent-purple);
        }

        .tokens {
            font-family: 'SF Mono', 'Fira Code', monospace;
            font-size: 0.875rem;
        }

        .tokens .input { color: var(--accent-blue); }
        .tokens .output { color: var(--accent-green); }
        .tokens .separator { color: var(--text-secondary); }

        .cost {
            font-family: 'SF Mono', 'Fira Code', monospace;
            color: var(--accent-orange);
        }

        .time {
            font-family: 'SF Mono', 'Fira Code', monospace;
            color: var(--text-secondary);
        }

        .model-summary {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 1rem;
            padding: 1.25rem;
        }

        .model-card {
            background: var(--bg-tertiary);
            border-radius: 8px;
            padding: 1.25rem;
        }

        .model-card h3 {
            font-size: 1rem;
            margin-bottom: 1rem;
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }

        .model-stats {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 0.75rem;
        }

        .stat {
            font-size: 0.875rem;
        }

        .stat-label {
            color: var(--text-secondary);
        }

        .stat-value {
            font-weight: 600;
            font-family: 'SF Mono', 'Fira Code', monospace;
        }

        .footer {
            text-align: center;
            color: var(--text-secondary);
            font-size: 0.875rem;
            margin-top: 2rem;
        }

        @media (max-width: 768px) {
            body { padding: 1rem; }
            .summary-cards { grid-template-columns: 1fr 1fr; }
            th, td { padding: 0.5rem; font-size: 0.875rem; }
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🚀 AI Benchmark Report</h1>
        <p class="subtitle">Generated: ${formatDate(Instant.now())} • Health Assistant Server</p>

        <div class="summary-cards">
            <div class="card">
                <div class="card-label">Tests Passed</div>
                <div class="card-value success">${totalPassed}/${results.size()}</div>
            </div>
            <div class="card">
                <div class="card-label">Total Tokens</div>
                <div class="card-value info">${String.format('%,d', totalTokens)}</div>
            </div>
            <div class="card">
                <div class="card-label">Total Cost</div>
                <div class="card-value warning">\$${String.format('%.4f', totalCost)}</div>
            </div>
            <div class="card">
                <div class="card-label">Avg Response Time</div>
                <div class="card-value">${String.format('%.1f', avgTime / 1000.0)}s</div>
            </div>
        </div>

        <div class="section">
            <div class="section-header">📊 Model Comparison</div>
            <div class="model-summary">
                ${models.collect { model ->
                    def modelResults = grouped[model]
                    def passed = modelResults.count { it.passed }
                    def failed = modelResults.count { !it.passed }
                    def inputTokens = modelResults.sum { it.inputTokens } ?: 0
                    def outputTokens = modelResults.sum { it.outputTokens } ?: 0
                    def modelCost = modelResults.sum { it.estimatedCostUsd } ?: 0.0
                    def modelAvgTime = modelResults.sum { it.responseTimeMs } / modelResults.size()
                    def modelShort = model?.replace("-preview", "")?.replace("gemini-", "") ?: "unknown"
                    def badgeClass = model?.contains("flash") ? "flash" : "pro"

                    """
                <div class="model-card">
                    <h3><span class="model-badge ${badgeClass}">${modelShort}</span></h3>
                    <div class="model-stats">
                        <div class="stat">
                            <div class="stat-label">Passed</div>
                            <div class="stat-value" style="color: var(--accent-green)">${passed}/${modelResults.size()}</div>
                        </div>
                        <div class="stat">
                            <div class="stat-label">Failed</div>
                            <div class="stat-value" style="color: var(--accent-red)">${failed}</div>
                        </div>
                        <div class="stat">
                            <div class="stat-label">Input Tokens</div>
                            <div class="stat-value" style="color: var(--accent-blue)">${String.format('%,d', inputTokens)}</div>
                        </div>
                        <div class="stat">
                            <div class="stat-label">Output Tokens</div>
                            <div class="stat-value" style="color: var(--accent-green)">${String.format('%,d', outputTokens)}</div>
                        </div>
                        <div class="stat">
                            <div class="stat-label">Total Cost</div>
                            <div class="stat-value" style="color: var(--accent-orange)">\$${String.format('%.4f', modelCost)}</div>
                        </div>
                        <div class="stat">
                            <div class="stat-label">Avg Time</div>
                            <div class="stat-value">${String.format('%.1f', modelAvgTime / 1000.0)}s</div>
                        </div>
                    </div>
                </div>
                    """
                }.join('')}
            </div>
        </div>

        <div class="section">
            <div class="section-header">📋 Test Results</div>
            <table>
                <thead>
                    <tr>
                        <th>Test ID</th>
                        <th>Test Name</th>
                        <th>Model</th>
                        <th>Status</th>
                        <th>Tokens (In/Out)</th>
                        <th>Cost</th>
                        <th>Time</th>
                    </tr>
                </thead>
                <tbody>
                    ${results.sort { a, b -> a.testId <=> b.testId ?: a.model <=> b.model }.collect { result ->
                        def modelShort = result.model?.replace("-preview", "")?.replace("gemini-", "") ?: "unknown"
                        def badgeClass = result.model?.contains("flash") ? "flash" : "pro"
                        def statusClass = result.passed ? "pass" : "fail"
                        def statusIcon = result.passed ? "✓" : "✗"
                        def statusText = result.passed ? "Pass" : "Fail"

                        """
                    <tr>
                        <td><strong>${result.testId}</strong></td>
                        <td>${result.testName ?: '-'}</td>
                        <td><span class="model-badge ${badgeClass}">${modelShort}</span></td>
                        <td><span class="status ${statusClass}">${statusIcon} ${statusText}</span></td>
                        <td class="tokens">
                            <span class="input">${String.format('%,d', result.inputTokens)}</span>
                            <span class="separator">/</span>
                            <span class="output">${String.format('%,d', result.outputTokens)}</span>
                        </td>
                        <td class="cost">\$${String.format('%.4f', result.estimatedCostUsd)}</td>
                        <td class="time">${String.format('%.2f', result.responseTimeMs / 1000.0)}s</td>
                    </tr>
                        """
                    }.join('')}
                </tbody>
            </table>
        </div>

        <div class="footer">
            <p>Generated by Health Assistant AI Benchmark Suite</p>
        </div>
    </div>
</body>
</html>
"""

        Files.createDirectories(outputPath.parent)
        outputPath.toFile().text = html
        println "HTML report written to: ${outputPath}"
    }

    /**
     * Generate Markdown report for GitHub Job Summary.
     */
    static void writeMarkdownReport(Path outputPath) {
        def grouped = results.groupBy { it.model }
        def models = grouped.keySet().sort()

        def totalPassed = results.count { it.passed }
        def totalFailed = results.count { !it.passed }
        def totalTokens = results.sum { it.totalTokens } ?: 0
        def totalCost = results.sum { it.estimatedCostUsd } ?: 0.0
        def avgTime = results.isEmpty() ? 0 : results.sum { it.responseTimeMs } / results.size()

        def sb = new StringBuilder()

        sb.append("# 🚀 AI Benchmark Report\n\n")
        sb.append("**Generated:** ${formatDate(Instant.now())}\n\n")

        // Summary badges
        def passRate = results.isEmpty() ? 0 : (totalPassed * 100 / results.size()) as int
        def passEmoji = passRate == 100 ? "✅" : passRate >= 80 ? "⚠️" : "❌"
        sb.append("${passEmoji} **${totalPassed}/${results.size()}** tests passed ")
        sb.append("| 🪙 **${String.format('%,d', totalTokens)}** tokens ")
        sb.append("| 💰 **\$${String.format('%.4f', totalCost)}** ")
        sb.append("| ⏱️ **${String.format('%.1f', avgTime / 1000.0)}s** avg\n\n")

        // Model comparison table
        sb.append("## 📊 Model Comparison\n\n")
        sb.append("| Model | Pass Rate | Input Tokens | Output Tokens | Cost | Avg Time |\n")
        sb.append("|-------|-----------|--------------|---------------|------|----------|\n")

        models.each { model ->
            def modelResults = grouped[model]
            def passed = modelResults.count { it.passed }
            def inputTokens = modelResults.sum { it.inputTokens } ?: 0
            def outputTokens = modelResults.sum { it.outputTokens } ?: 0
            def modelCost = modelResults.sum { it.estimatedCostUsd } ?: 0.0
            def modelAvgTime = modelResults.sum { it.responseTimeMs } / modelResults.size()
            def modelShort = model?.replace("-preview", "")?.replace("gemini-", "") ?: "unknown"
            def modelEmoji = passed == modelResults.size() ? "✅" : "⚠️"

            sb.append("| **${modelShort}** ")
            sb.append("| ${modelEmoji} ${passed}/${modelResults.size()} ")
            sb.append("| ${String.format('%,d', inputTokens)} ")
            sb.append("| ${String.format('%,d', outputTokens)} ")
            sb.append("| \$${String.format('%.4f', modelCost)} ")
            sb.append("| ${String.format('%.1fs', modelAvgTime / 1000.0)} |\n")
        }

        sb.append("\n")

        // Detailed results table
        sb.append("## 📋 Test Results\n\n")
        sb.append("| Test | Model | Status | Tokens | Cost | Time |\n")
        sb.append("|------|-------|--------|--------|------|------|\n")

        results.sort { a, b -> a.testId <=> b.testId ?: a.model <=> b.model }.each { result ->
            def modelShort = result.model?.replace("-preview", "")?.replace("gemini-", "") ?: "unknown"
            def statusEmoji = result.passed ? "✅" : "❌"
            def tokensStr = "${result.inputTokens}/${result.outputTokens}"

            sb.append("| ${result.testId}: ${result.testName ?: '-'} ")
            sb.append("| ${modelShort} ")
            sb.append("| ${statusEmoji} ")
            sb.append("| ${tokensStr} ")
            sb.append("| \$${String.format('%.4f', result.estimatedCostUsd)} ")
            sb.append("| ${String.format('%.2fs', result.responseTimeMs / 1000.0)} |\n")
        }

        // Failed tests details
        def failedTests = results.findAll { !it.passed }
        if (failedTests) {
            sb.append("\n## ❌ Failed Tests\n\n")
            failedTests.each { result ->
                def modelShort = result.model?.replace("-preview", "")?.replace("gemini-", "") ?: "unknown"
                sb.append("### ${result.testId} - ${modelShort}\n")
                sb.append("**Test:** ${result.testName}\n\n")
                if (result.errorMessage) {
                    sb.append("**Error:**\n```\n${result.errorMessage}\n```\n\n")
                }
            }
        }

        Files.createDirectories(outputPath.parent)
        outputPath.toFile().text = sb.toString()
        println "Markdown report written to: ${outputPath}"
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
