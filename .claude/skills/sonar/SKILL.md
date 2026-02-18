---
name: sonar
description: Check SonarCloud code quality status for the Health-Assistant-Server project
disable-model-invocation: true
argument-hint: "[all|gate|issues|metrics|hotspots]"
---

# SonarCloud Quality Check

**Project key**: `Dawid-Cisowski_Health-Assistant-Server`

## Usage

- `/sonar` or `/sonar all` - Full overview (quality gate + issues + metrics)
- `/sonar gate` - Quality gate status (PASSED/FAILED + conditions)
- `/sonar issues` - Current issues grouped by severity (top 5 most critical)
- `/sonar metrics` - Coverage, duplications, tech debt, ratings
- `/sonar hotspots` - Security hotspots to review

## Instructions

Based on the argument (default: `all`), call the appropriate MCP tools below. Always use project key `Dawid-Cisowski_Health-Assistant-Server`.

### `gate` - Quality Gate Status

Call `mcp__sonarqube__get_project_quality_gate_status` with:
- `projectKey`: `Dawid-Cisowski_Health-Assistant-Server`

Present the result as:
- Overall status: PASSED or FAILED (use checkmark/cross)
- List each condition with its actual value vs threshold
- Highlight any failing conditions

### `issues` - Current Issues

Call `mcp__sonarqube__search_sonar_issues_in_projects` with:
- `projects`: `Dawid-Cisowski_Health-Assistant-Server`
- `resolved`: `false`
- `pageSize`: `20`

Present the result as:
- Total count of open issues
- Group by severity (BLOCKER, CRITICAL, MAJOR, MINOR, INFO)
- Show top 5 most critical issues with file path, line, and message
- Suggest which issues to fix first

### `metrics` - Code Metrics

Call `mcp__sonarqube__get_component_measures` with:
- `component`: `Dawid-Cisowski_Health-Assistant-Server`
- `metricKeys`: `coverage,duplicated_lines_density,sqale_debt_ratio,bugs,vulnerabilities,code_smells,security_hotspots,reliability_rating,security_rating,sqale_rating,ncloc`

Present the result as a formatted table:
- Coverage: X%
- Duplications: X%
- Tech Debt Ratio: X%
- Bugs / Vulnerabilities / Code Smells / Security Hotspots counts
- Reliability / Security / Maintainability ratings (A-E)
- Lines of Code

### `hotspots` - Security Hotspots

Call `mcp__sonarqube__search_sonar_issues_in_projects` with:
- `projects`: `Dawid-Cisowski_Health-Assistant-Server`
- `resolved`: `false`
- `types`: `SECURITY_HOTSPOT`

Present the result as:
- Total count of open security hotspots
- List each hotspot with file path, line, message, and category
- Prioritize by review priority (HIGH, MEDIUM, LOW)

### `all` - Full Overview

Execute `gate`, `metrics`, and `issues` in sequence. Present a consolidated report with sections for each.
