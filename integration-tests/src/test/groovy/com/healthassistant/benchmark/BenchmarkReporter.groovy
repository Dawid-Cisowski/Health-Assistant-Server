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

    /**
     * Update the passed status of the last recorded result for a given test ID.
     * Used after LLM judge evaluation to reflect actual pass/fail status.
     */
    static void updateLastResult(String testId, boolean passed, String errorMessage = null) {
        def lastResult = results.reverse().find { it.testId == testId }
        if (lastResult) {
            lastResult.passed = passed
            if (errorMessage) {
                lastResult.errorMessage = errorMessage
            }
        }
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
        println "-" * 80
        models.each { model ->
            def modelResults = grouped[model]
            def passed = modelResults.count { it.passed }
            def total = modelResults.size()
            def totalInputTokens = modelResults.sum { it.inputTokens } ?: 0
            def totalOutputTokens = modelResults.sum { it.outputTokens } ?: 0
            def totalCost = modelResults.sum { it.estimatedCostUsd } ?: 0.0
            def totalTime = modelResults.sum { it.responseTimeMs } ?: 0
            def avgTime = totalTime / modelResults.size()
            def modelShort = model?.replace("-preview", "")?.replace("gemini-", "") ?: "unknown"

            printf "%-20s: %d/%d passed │ Tokens: %d/%d │ Cost: \$%.4f%n",
                    modelShort, passed, total, totalInputTokens, totalOutputTokens, totalCost
            printf "                    │ Total Time: %.1fs │ Avg Time: %.1fs%n",
                    totalTime / 1000.0, avgTime / 1000.0
        }

        println "=" * 90
        println ""

        // Cost projections
        println "COST PROJECTIONS (Monthly):"
        println "-" * 80
        printf "%-20s │ %-15s │ %-15s │ %-15s%n", "Model", "Per Request", "1K/day (30d)", "10K/day (30d)"
        println "-" * 80
        models.each { model ->
            def modelResults = grouped[model]
            def avgCostPerRequest = (modelResults.sum { it.estimatedCostUsd } ?: 0.0) / modelResults.size()
            def cost1kMonth = avgCostPerRequest * 1000 * 30
            def cost10kMonth = avgCostPerRequest * 10000 * 30
            def modelShort = model?.replace("-preview", "")?.replace("gemini-", "") ?: "unknown"

            printf "%-20s │ \$%-14.6f │ \$%-14.2f │ \$%-14.2f%n",
                    modelShort, avgCostPerRequest, cost1kMonth, cost10kMonth
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
    /**
     * Generate Modern HTML Dashboard for GitHub Pages
     */
    static void writeHtmlReport(Path outputPath) {
        def grouped = results.groupBy { it.model }
        def models = grouped.keySet().sort()

        // --- 1. PREPARE DATA ---
        def totalPassed = results.count { it.passed }
        def totalTests = results.size()
        def passRate = totalTests > 0 ? (totalPassed / totalTests * 100).toInteger() : 0

        // Cost calculations
        def flashModel = models.find { it.contains("flash") }
        def proModel = models.find { it.contains("pro") }

        def flashCost = flashModel ? (grouped[flashModel].sum { it.estimatedCostUsd } / grouped[flashModel].size() * 10000 * 30) : 0
        def proCost = proModel ? (grouped[proModel].sum { it.estimatedCostUsd } / grouped[proModel].size() * 10000 * 30) : 0

        def savingsPercent = (proCost > 0 && flashCost > 0) ? ((1 - (flashCost / proCost)) * 100).toInteger() : 0
        def savingsAmount = proCost - flashCost

        // Latency
        def flashTime = flashModel ? (grouped[flashModel].sum { it.responseTimeMs } / grouped[flashModel].size() / 1000.0) : 0
        def proTime = proModel ? (grouped[proModel].sum { it.responseTimeMs } / grouped[proModel].size() / 1000.0) : 0

        // HTML Content
        def html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AI Benchmark Dashboard</title>
    <style>
        :root {
            --bg-body: #0d1117;
            --bg-card: #161b22;
            --bg-header: #010409;
            --border: #30363d;
            --text-main: #c9d1d9;
            --text-muted: #8b949e;
            --color-flash: #58a6ff;
            --color-pro: #d29922;
            --color-success: #3fb950;
            --color-danger: #f85149;
        }

        * { box-sizing: border-box; margin: 0; padding: 0; }
        
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
            background-color: var(--bg-body);
            color: var(--text-main);
            line-height: 1.6;
            padding-bottom: 50px;
        }

        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }

        /* --- HEADER --- */
        header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 40px;
            padding-bottom: 20px;
            border-bottom: 1px solid var(--border);
        }
        
        h1 { font-size: 1.8rem; font-weight: 600; }
        .badge-live { 
            background: rgba(63, 185, 80, 0.15); 
            color: var(--color-success); 
            padding: 5px 12px; 
            border-radius: 20px; 
            font-size: 0.8rem; 
            border: 1px solid var(--color-success);
            font-weight: 600;
        }

        /* --- KPI GRID --- */
        .kpi-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-bottom: 40px;
        }

        .card {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 25px;
            position: relative;
            overflow: hidden;
        }

        .card-label {
            font-size: 0.85rem;
            color: var(--text-muted);
            text-transform: uppercase;
            letter-spacing: 0.05em;
            font-weight: 600;
            margin-bottom: 10px;
        }

        .card-value {
            font-size: 2.5rem;
            font-weight: 700;
            margin-bottom: 5px;
        }

        .card-sub { font-size: 0.9rem; color: var(--text-muted); }
        .text-flash { color: var(--color-flash); }
        .text-pro { color: var(--color-pro); }
        .text-success { color: var(--color-success); }

        /* --- CHART SECTION --- */
        .chart-container {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 30px;
            margin-bottom: 40px;
        }

        .chart-title { margin-bottom: 20px; font-weight: 600; }

        .bar-wrapper {
            margin-bottom: 25px;
        }

        .bar-info {
            display: flex;
            justify-content: space-between;
            margin-bottom: 8px;
            font-size: 0.9rem;
        }

        .progress-bg {
            background: #21262d;
            height: 24px;
            border-radius: 6px;
            overflow: hidden;
            position: relative;
        }

        .progress-fill {
            height: 100%;
            display: flex;
            align-items: center;
            padding-right: 10px;
            justify-content: flex-end;
            font-size: 0.8rem;
            font-weight: bold;
            color: #fff;
            transition: width 1s ease-in-out;
            width: 0%; /* Will be animated by JS */
        }

        /* --- TABLE --- */
        .table-controls {
            margin-bottom: 15px;
            display: flex;
            gap: 10px;
        }
        
        button.btn-filter {
            background: var(--bg-card);
            border: 1px solid var(--border);
            color: var(--text-main);
            padding: 8px 16px;
            border-radius: 6px;
            cursor: pointer;
            font-size: 0.9rem;
        }
        
        button.btn-filter:hover, button.btn-filter.active {
            background: var(--border);
        }

        .data-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 0.9rem;
        }

        .data-table th {
            text-align: left;
            padding: 12px 15px;
            background: #21262d;
            color: var(--text-muted);
            font-weight: 600;
            border-bottom: 1px solid var(--border);
        }

        .data-table td {
            padding: 12px 15px;
            border-bottom: 1px solid var(--border);
            font-family: 'SF Mono', Consolas, monospace;
        }

        .data-table tr:hover { background: #21262d; }
        
        .status-pill {
            padding: 4px 10px;
            border-radius: 12px;
            font-size: 0.75rem;
            font-weight: 600;
        }
        
        .status-pass { background: rgba(63, 185, 80, 0.15); color: var(--color-success); }
        .status-fail { background: rgba(248, 81, 73, 0.15); color: var(--color-danger); }

    </style>
</head>
<body>

<div class="container">
    <header>
        <div>
            <h1>🚀 AI Benchmark Dashboard</h1>
            <div style="color: var(--text-muted); font-size: 0.9rem; margin-top: 5px;">
                Generated: ${formatDate(Instant.now())}
            </div>
        </div>
        <div class="badge-live">LIVE REPORT</div>
    </header>

    <div class="kpi-grid">
        <div class="card">
            <div class="card-label">Cost Reduction</div>
            <div class="card-value text-flash">${savingsPercent}%</div>
            <div class="card-sub">Flash vs Pro (Monthly)</div>
        </div>
        
        <div class="card">
            <div class="card-label">Pass Rate</div>
            <div class="card-value text-success">${passRate}%</div>
            <div class="card-sub">${totalPassed} / ${totalTests} tests passed</div>
        </div>

        <div class="card">
            <div class="card-label">Avg Latency</div>
            <div class="card-value" style="color: white;">${String.format('%.1f', flashTime)}s</div>
            <div class="card-sub">vs ${String.format('%.1f', proTime)}s (Pro)</div>
        </div>
    </div>

    <div class="chart-container">
        <div class="chart-title">💰 Monthly Cost Projection (10k requests/day)</div>
        
        <div class="bar-wrapper">
            <div class="bar-info">
                <span>⚡ <strong>Gemini Flash</strong></span>
                <span class="text-flash">\$${String.format('%.2f', flashCost)}</span>
            </div>
            <div class="progress-bg">
                <div class="progress-fill" style="background: var(--color-flash); width: 0%" data-width="${(flashCost / proCost * 100).toInteger()}%"></div>
            </div>
        </div>

        <div class="bar-wrapper">
            <div class="bar-info">
                <span>🧠 <strong>Gemini Pro</strong></span>
                <span class="text-pro">\$${String.format('%.2f', proCost)}</span>
            </div>
            <div class="progress-bg">
                <div class="progress-fill" style="background: var(--color-pro); width: 0%" data-width="100%"></div>
            </div>
        </div>
        
        <div style="margin-top: 15px; font-size: 0.9rem; color: var(--text-muted); text-align: right;">
            Est. annual savings: <strong style="color: white;">\$${String.format('%,.0f', savingsAmount * 12)}</strong>
        </div>
    </div>

    <div class="table-controls">
        <button class="btn-filter active" onclick="filterTable('all')">All Tests</button>
        <button class="btn-filter" onclick="filterTable('fail')">Failed Only</button>
    </div>

    <table class="data-table" id="benchmarkTable">
        <thead>
            <tr>
                <th>Test Case</th>
                <th>Model</th>
                <th>Status</th>
                <th>Tokens (In/Out)</th>
                <th>Cost</th>
                <th>Time</th>
            </tr>
        </thead>
        <tbody>
            ${results.collect { r ->
            def statusClass = r.passed ? 'status-pass' : 'status-fail'
            def statusText = r.passed ? 'PASS' : 'FAIL'
            def modelClass = r.model.contains('flash') ? 'text-flash' : 'text-pro'
            def rowClass = r.passed ? 'row-pass' : 'row-fail'
            """
                <tr class="${rowClass}">
                    <td style="font-weight: 500;">${r.testName} <div style="font-size:0.75rem; color:#8b949e;">${r.testId}</div></td>
                    <td class="${modelClass}">${r.model.replace('gemini-','').replace('-preview','')}</td>
                    <td><span class="status-pill ${statusClass}">${statusText}</span></td>
                    <td>${r.inputTokens} / ${r.outputTokens}</td>
                    <td>\$${String.format('%.4f', r.estimatedCostUsd)}</td>
                    <td>${String.format('%.2f', r.responseTimeMs/1000)}s</td>
                </tr>
                """
        }.join('\n')}
        </tbody>
    </table>
</div>

<script>
    // Animate bars on load
    window.onload = function() {
        document.querySelectorAll('.progress-fill').forEach(bar => {
            bar.style.width = bar.getAttribute('data-width');
        });
    };

    // Simple Table Filter
    function filterTable(type) {
        document.querySelectorAll('.btn-filter').forEach(b => b.classList.remove('active'));
        event.target.classList.add('active');
        
        const rows = document.querySelectorAll('#benchmarkTable tbody tr');
        rows.forEach(row => {
            if (type === 'all') {
                row.style.display = '';
            } else if (type === 'fail') {
                row.style.display = row.classList.contains('row-fail') ? '' : 'none';
            }
        });
    }
</script>

</body>
</html>
        """

        // Write file
        Files.createDirectories(outputPath.parent)
        outputPath.toFile().text = html
        println "HTML Dashboard written to: ${outputPath}"
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
        def totalTime = results.sum { it.responseTimeMs } ?: 0
        def avgTime = results.isEmpty() ? 0 : totalTime / results.size()

        def sb = new StringBuilder()

        sb.append("# 🚀 AI Benchmark Report\n\n")
        sb.append("**Generated:** ${formatDate(Instant.now())}\n\n")

        // Summary badges
        def passRate = results.isEmpty() ? 0 : (totalPassed * 100 / results.size()) as int
        def passEmoji = passRate == 100 ? "✅" : passRate >= 80 ? "⚠️" : "❌"
        sb.append("${passEmoji} **${totalPassed}/${results.size()}** tests passed ")
        sb.append("| 🪙 **${String.format('%,d', totalTokens)}** tokens ")
        sb.append("| 💰 **\$${String.format('%.4f', totalCost)}** ")
        sb.append("| ⏱️ **${String.format('%.1f', totalTime / 1000.0)}s** total ")
        sb.append("| **${String.format('%.1f', avgTime / 1000.0)}s** avg\n\n")

        // Model comparison table
        sb.append("## 📊 Model Comparison\n\n")
        sb.append("| Model | Pass Rate | Input Tokens | Output Tokens | Cost | Total Time | Avg Time |\n")
        sb.append("|-------|-----------|--------------|---------------|------|------------|----------|\n")

        models.each { model ->
            def modelResults = grouped[model]
            def passed = modelResults.count { it.passed }
            def inputTokens = modelResults.sum { it.inputTokens } ?: 0
            def outputTokens = modelResults.sum { it.outputTokens } ?: 0
            def modelCost = modelResults.sum { it.estimatedCostUsd } ?: 0.0
            def modelTotalTime = modelResults.sum { it.responseTimeMs } ?: 0
            def modelAvgTime = modelTotalTime / modelResults.size()
            def modelShort = model?.replace("-preview", "")?.replace("gemini-", "") ?: "unknown"
            def modelEmoji = passed == modelResults.size() ? "✅" : "⚠️"

            sb.append("| **${modelShort}** ")
            sb.append("| ${modelEmoji} ${passed}/${modelResults.size()} ")
            sb.append("| ${String.format('%,d', inputTokens)} ")
            sb.append("| ${String.format('%,d', outputTokens)} ")
            sb.append("| \$${String.format('%.4f', modelCost)} ")
            sb.append("| ${String.format('%.1fs', modelTotalTime / 1000.0)} ")
            sb.append("| ${String.format('%.1fs', modelAvgTime / 1000.0)} |\n")
        }

        sb.append("\n")

        // Cost projections section (after Model Comparison)
        sb.append("## 💰 Monthly Cost Projections\n\n")
        sb.append("Based on average cost per request, projected monthly costs at scale:\n\n")
        sb.append("| Model | Per Request | 1K/day (30d) | 10K/day (30d) |\n")
        sb.append("|-------|-------------|--------------|---------------|\n")

        // Calculate projections
        def costProjections = models.collect { model ->
            def modelResults = grouped[model]
            def avgCostPerRequest = (modelResults.sum { it.estimatedCostUsd } ?: 0.0) / modelResults.size()
            [
                modelShort: model?.replace("-preview", "")?.replace("gemini-", "") ?: "unknown",
                avgCost: avgCostPerRequest,
                cost1kMonth: avgCostPerRequest * 1000 * 30,
                cost10kMonth: avgCostPerRequest * 10000 * 30
            ]
        }
        def maxCost = costProjections.max { it.cost10kMonth }?.cost10kMonth ?: 1

        costProjections.each { proj ->
            sb.append("| **${proj.modelShort}** ")
            sb.append("| \$${String.format('%.6f', proj.avgCost)} ")
            sb.append("| \$${String.format('%.2f', proj.cost1kMonth)} ")
            sb.append("| \$${String.format('%.2f', proj.cost10kMonth)} |\n")
        }

        // ASCII bar chart - grouped by scale to show cost growth
        sb.append("\n### 📊 Cost Visualization (Monthly)\n\n")
        sb.append("```\n")
        sb.append("1K requests/day:\n")
        costProjections.each { proj ->
            def barLength = Math.max(1, (proj.cost1kMonth / maxCost * 40) as int)
            def bar = "█" * barLength
            sb.append(String.format("  %-13s │ %s \$%.2f\n", proj.modelShort, bar, proj.cost1kMonth))
        }
        sb.append("\n10K requests/day:\n")
        costProjections.each { proj ->
            def barLength = Math.max(1, (proj.cost10kMonth / maxCost * 40) as int)
            def bar = "█" * barLength
            sb.append(String.format("  %-13s │ %s \$%.2f\n", proj.modelShort, bar, proj.cost10kMonth))
        }
        sb.append("```\n\n")

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
