package com.healthassistant

import com.healthassistant.dailysummary.api.DailySummaryFacade
import com.healthassistant.reports.api.ReportsFacade
import com.healthassistant.reports.api.dto.ReportType
import groovy.json.JsonOutput
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Title

import java.time.LocalDate
import java.time.ZoneId

/**
 * Integration tests for the Health Reports module.
 *
 * Tests cover: daily/weekly report generation, goals evaluation,
 * period comparison, REST API endpoints, device isolation, and upsert behavior.
 */
@Title("Feature: Health Reports")
class HealthReportSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-reports"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw")

    // Use fixed dates far enough in the past to avoid collisions
    private static final LocalDate DAY_1 = LocalDate.of(2025, 11, 12)
    private static final LocalDate DAY_2 = LocalDate.of(2025, 11, 13)

    @Autowired
    ReportsFacade reportsFacade

    @Autowired
    DailySummaryFacade dailySummaryFacade

    @Autowired
    JdbcTemplate jdbcTemplate

    def setup() {
        // Clean reports for test device
        jdbcTemplate.update("DELETE FROM health_reports WHERE device_id = ?", DEVICE_ID)
        // Clean projections for test dates
        cleanupProjectionsForDateRange(DEVICE_ID, LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30))
        // Clean events
        cleanupEventsForDevice(DEVICE_ID)
    }

    // ==================== Daily Report Generation ====================

    def "Scenario 1: Generate daily report with comprehensive data and verify goals"() {
        given: "health events for 2025-11-12"
        submitHealthEventsForDate(DAY_1, [
            steps: 12000,
            sleepMinutes: 450,
            activeMinutes: 250,
            activeCalories: 700,
            meals: [
                [title: "Sniadanie", type: "BREAKFAST", kcal: 400, protein: 20, fat: 15, carbs: 50, rating: "HEALTHY"],
                [title: "Obiad", type: "LUNCH", kcal: 650, protein: 40, fat: 20, carbs: 60, rating: "HEALTHY"]
            ],
            restingHr: 65
        ])

        and: "wait for resting HR projection to be created by async listener"
        waitForEventProcessing(10) {
            def count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM resting_heart_rate_projections WHERE device_id = ? AND date = ?",
                Integer, DEVICE_ID, java.sql.Date.valueOf(DAY_1))
            count > 0
        }

        and: "regenerate daily summary so it includes the resting HR data"
        dailySummaryFacade.generateDailySummary(DEVICE_ID, DAY_1)

        when: "generating daily report"
        def reportIdOpt = reportsFacade.generateReport(DEVICE_ID, ReportType.DAILY, DAY_1, DAY_1)

        then: "report is generated"
        reportIdOpt.isPresent()

        and: "report detail contains correct goals"
        def report = reportsFacade.getReport(DEVICE_ID, reportIdOpt.get()).get()
        report.reportType() == ReportType.DAILY
        report.periodStart() == DAY_1
        report.periodEnd() == DAY_1
        report.goals() != null
        report.goals().total() >= 6  // At least steps, sleep, activeMinutes, burnedCalories, healthyMeals + restingHr

        and: "steps goal is achieved (12000 >= 10000)"
        def stepsGoal = report.goals().details().find { it.name() == "steps" }
        stepsGoal != null
        stepsGoal.achieved()

        and: "sleep goal is achieved (450 >= 420)"
        def sleepGoal = report.goals().details().find { it.name() == "sleep" }
        sleepGoal != null
        sleepGoal.achieved()

        and: "active minutes goal is achieved (250 >= 240)"
        def activeGoal = report.goals().details().find { it.name() == "activeMinutes" }
        activeGoal != null
        activeGoal.achieved()

        and: "burned calories goal is achieved (700 >= 600)"
        def caloriesGoal = report.goals().details().find { it.name() == "burnedCalories" }
        caloriesGoal != null
        caloriesGoal.achieved()

        and: "healthy meals goal is NOT achieved (2 < 3)"
        def mealsGoal = report.goals().details().find { it.name() == "healthyMeals" }
        mealsGoal != null
        !mealsGoal.achieved()

        and: "resting HR goal is achieved (65 in 50-80)"
        def hrGoal = report.goals().details().find { it.name() == "restingHr" }
        hrGoal != null
        hrGoal.achieved()

        and: "short summary contains goal counts"
        report.shortSummary() != null
        report.shortSummary().contains("/")
    }

    def "Scenario 2: Generate daily report and verify data snapshot"() {
        given: "health events for 2025-11-12"
        submitHealthEventsForDate(DAY_1, [
            steps: 8500,
            sleepMinutes: 400,
            activeMinutes: 180,
            activeCalories: 450,
            meals: [
                [title: "Lunch", type: "LUNCH", kcal: 600, protein: 35, fat: 18, carbs: 55, rating: "NEUTRAL"]
            ]
        ])

        when: "generating daily report"
        def reportIdOpt = reportsFacade.generateReport(DEVICE_ID, ReportType.DAILY, DAY_1, DAY_1)

        then: "report is generated with data snapshot"
        reportIdOpt.isPresent()
        def report = reportsFacade.getReport(DEVICE_ID, reportIdOpt.get()).get()
        report.data() != null

        and: "activity data is correct"
        report.data().activity() != null
        report.data().activity().steps() == 8500

        and: "sleep data is present"
        report.data().sleep() != null
        report.data().sleep().totalMinutes() == 400

        and: "AI summary is present (from mock ChatModel)"
        report.aiSummary() != null
        !report.aiSummary().isBlank()
    }

    def "Scenario 3: Daily report includes comparison with previous day"() {
        given: "health events for two consecutive days"
        submitHealthEventsForDate(DAY_1, [steps: 8000, sleepMinutes: 380, activeCalories: 400])
        submitHealthEventsForDate(DAY_2, [steps: 12000, sleepMinutes: 450, activeCalories: 650])

        when: "generating report for second day"
        def reportIdOpt = reportsFacade.generateReport(DEVICE_ID, ReportType.DAILY, DAY_2, DAY_2)

        then: "report includes comparison"
        reportIdOpt.isPresent()
        def report = reportsFacade.getReport(DEVICE_ID, reportIdOpt.get()).get()
        report.comparison() != null
        report.comparison().previousPeriodStart() == DAY_1

        and: "comparison has metrics with positive changes"
        def stepsMetric = report.comparison().metrics().find { it.name() == "steps" }
        stepsMetric != null
        stepsMetric.currentValue().intValue() == 12000
        stepsMetric.previousValue().intValue() == 8000
        stepsMetric.changePercent() > 0  // 50% increase
    }

    // ==================== REST API Endpoints ====================

    def "Scenario 4: List reports via REST API with pagination"() {
        given: "multiple reports for different dates"
        submitHealthEventsForDate(DAY_1, [steps: 5000])
        submitHealthEventsForDate(DAY_2, [steps: 6000])

        and: "wait for async daily summaries to be created"
        waitForEventProcessing(10) {
            def count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_summaries WHERE device_id = ? AND date IN (?, ?)",
                Integer, DEVICE_ID, java.sql.Date.valueOf(DAY_1), java.sql.Date.valueOf(DAY_2))
            count >= 2
        }
        reportsFacade.generateReport(DEVICE_ID, ReportType.DAILY, DAY_1, DAY_1)
        reportsFacade.generateReport(DEVICE_ID, ReportType.DAILY, DAY_2, DAY_2)

        when: "listing reports via REST API"
        def path = "/v1/reports?type=DAILY&page=0&size=10"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, path)
                .get(path)
                .then()
                .extract()

        then: "reports are returned"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getInt("totalElements") >= 2
        def content = body.getList("content")
        content.size() >= 2

        and: "each report has required summary fields"
        content.every { it.reportId != null }
        content.every { it.reportType == "DAILY" }
        content.every { it.shortSummary != null }
        content.every { it.goalsTotal > 0 }
    }

    def "Scenario 5: Get report detail via REST API"() {
        given: "a generated report"
        submitHealthEventsForDate(DAY_1, [steps: 10500, sleepMinutes: 420])
        def reportId = reportsFacade.generateReport(DEVICE_ID, ReportType.DAILY, DAY_1, DAY_1).get()

        when: "getting report detail via REST API"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/reports/${reportId}")
                .get("/v1/reports/${reportId}")
                .then()
                .extract()

        then: "full report detail is returned"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getLong("reportId") == reportId
        body.getString("reportType") == "DAILY"
        body.getString("periodStart") == DAY_1.toString()

        and: "goals section is present"
        body.getInt("goals.total") >= 4
        body.getList("goals.details").size() >= 4

        and: "data snapshot is present"
        body.get("data") != null
        body.get("data.activity") != null
    }

    def "Scenario 6: Device isolation - cannot access another device's report"() {
        given: "a report generated for test device"
        submitHealthEventsForDate(DAY_1, [steps: 7000])
        def reportId = reportsFacade.generateReport(DEVICE_ID, ReportType.DAILY, DAY_1, DAY_1).get()

        when: "another device tries to access the report"
        def response = authenticatedGetRequest("different-device-id", DIFFERENT_DEVICE_SECRET_BASE64, "/v1/reports/${reportId}")
                .get("/v1/reports/${reportId}")
                .then()
                .extract()

        then: "access is denied with 404"
        response.statusCode() == 404
    }

    // ==================== Weekly Report ====================

    def "Scenario 7: Generate weekly report with aggregated data"() {
        given: "health events for a full week"
        def weekStart = LocalDate.of(2025, 11, 10) // Monday
        def weekEnd = LocalDate.of(2025, 11, 16)   // Sunday

        // Clean the full week range
        cleanupProjectionsForDateRange(DEVICE_ID, weekStart, weekEnd)

        // Submit varying data for each day
        (0..6).each { dayOffset ->
            def date = weekStart.plusDays(dayOffset)
            submitHealthEventsForDate(date, [
                steps: 8000 + (dayOffset * 500),
                sleepMinutes: 400 + (dayOffset * 10),
                activeCalories: 300 + (dayOffset * 50)
            ])
        }

        when: "generating weekly report"
        def reportIdOpt = reportsFacade.generateReport(DEVICE_ID, ReportType.WEEKLY, weekStart, weekEnd)

        then: "report is generated"
        reportIdOpt.isPresent()
        def report = reportsFacade.getReport(DEVICE_ID, reportIdOpt.get()).get()
        report.reportType() == ReportType.WEEKLY
        report.periodStart() == weekStart
        report.periodEnd() == weekEnd

        and: "weekly goals are evaluated"
        report.goals() != null
        report.goals().total() >= 4  // avgSteps, avgSleep, workouts, activeMinutes + nutrition

        and: "range data snapshot is present (not daily data)"
        report.rangeData() != null
        report.data() == null  // daily data should be null for weekly
    }

    // ==================== Upsert Behavior ====================

    def "Scenario 8: Regenerating report updates existing instead of duplicating"() {
        given: "a generated report"
        submitHealthEventsForDate(DAY_1, [steps: 5000])
        reportsFacade.generateReport(DEVICE_ID, ReportType.DAILY, DAY_1, DAY_1)

        and: "initial report count"
        def initialCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM health_reports WHERE device_id = ? AND report_type = 'DAILY'",
            Long, DEVICE_ID
        )

        when: "regenerating the same report"
        reportsFacade.generateReport(DEVICE_ID, ReportType.DAILY, DAY_1, DAY_1)

        then: "report count stays the same (upsert, not insert)"
        def newCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM health_reports WHERE device_id = ? AND report_type = 'DAILY'",
            Long, DEVICE_ID
        )
        newCount == initialCount
    }

    def "Scenario 9: Report returns 404 for non-existent ID"() {
        when: "requesting a non-existent report"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/reports/999999")
                .get("/v1/reports/999999")
                .then()
                .extract()

        then: "404 is returned"
        response.statusCode() == 404
    }

    def "Scenario 10: Daily report with no data returns empty"() {
        given: "no health events for the date"
        def emptyDate = LocalDate.of(2025, 11, 20)
        cleanupProjectionsForDevice(DEVICE_ID, emptyDate)

        when: "generating daily report"
        def reportIdOpt = reportsFacade.generateReport(DEVICE_ID, ReportType.DAILY, emptyDate, emptyDate)

        then: "no report is generated"
        reportIdOpt.isEmpty()
    }

    // ==================== Event Submission Helpers ====================

    private void submitHealthEventsForDate(LocalDate date, Map data) {
        def zoned = date.atStartOfDay(POLAND_ZONE)
        def events = []

        // Steps
        if (data.steps) {
            def bucketStart = zoned.plusHours(10).toInstant()
            def bucketEnd = zoned.plusHours(11).toInstant()
            events << [
                idempotencyKey: "report-steps-${date}-${DEVICE_ID}",
                type: "StepsBucketedRecorded.v1",
                occurredAt: bucketEnd.toString(),
                payload: [
                    bucketStart: bucketStart.toString(),
                    bucketEnd: bucketEnd.toString(),
                    count: data.steps,
                    originPackage: "com.test"
                ]
            ]
        }

        // Sleep
        if (data.sleepMinutes) {
            def sleepEnd = zoned.plusHours(7).toInstant()
            def sleepStart = sleepEnd.minusSeconds(data.sleepMinutes * 60L)
            events << [
                idempotencyKey: "report-sleep-${date}-${DEVICE_ID}",
                type: "SleepSessionRecorded.v1",
                occurredAt: sleepEnd.toString(),
                payload: [
                    sleepId: "report-sleep-${date}",
                    sleepStart: sleepStart.toString(),
                    sleepEnd: sleepEnd.toString(),
                    totalMinutes: data.sleepMinutes,
                    originPackage: "com.test"
                ]
            ]
        }

        // Active minutes
        if (data.activeMinutes) {
            def bucketStart = zoned.plusHours(12).toInstant()
            def bucketEnd = zoned.plusHours(13).toInstant()
            events << [
                idempotencyKey: "report-active-min-${date}-${DEVICE_ID}",
                type: "ActiveMinutesRecorded.v1",
                occurredAt: bucketEnd.toString(),
                payload: [
                    bucketStart: bucketStart.toString(),
                    bucketEnd: bucketEnd.toString(),
                    activeMinutes: data.activeMinutes,
                    originPackage: "com.test"
                ]
            ]
        }

        // Active calories
        if (data.activeCalories) {
            def bucketStart = zoned.plusHours(14).toInstant()
            def bucketEnd = zoned.plusHours(15).toInstant()
            events << [
                idempotencyKey: "report-active-cal-${date}-${DEVICE_ID}",
                type: "ActiveCaloriesBurnedRecorded.v1",
                occurredAt: bucketEnd.toString(),
                payload: [
                    bucketStart: bucketStart.toString(),
                    bucketEnd: bucketEnd.toString(),
                    energyKcal: data.activeCalories,
                    originPackage: "com.test"
                ]
            ]
        }

        // Meals
        if (data.meals) {
            data.meals.eachWithIndex { meal, idx ->
                def occurredAt = zoned.plusHours(8 + idx * 4).toInstant()
                events << [
                    idempotencyKey: "report-meal-${date}-${idx}-${DEVICE_ID}",
                    type: "MealRecorded.v1",
                    occurredAt: occurredAt.toString(),
                    payload: [
                        title: meal.title,
                        mealType: meal.type,
                        caloriesKcal: meal.kcal,
                        proteinGrams: meal.protein,
                        fatGrams: meal.fat,
                        carbohydratesGrams: meal.carbs,
                        healthRating: meal.rating
                    ]
                ]
            }
        }

        // Resting heart rate
        if (data.restingHr) {
            def measuredAt = zoned.plusHours(6).toInstant()
            events << [
                idempotencyKey: "report-rhr-${date}-${DEVICE_ID}",
                type: "RestingHeartRateRecorded.v1",
                occurredAt: measuredAt.toString(),
                payload: [
                    measuredAt: measuredAt.toString(),
                    restingBpm: data.restingHr,
                    originPackage: "com.test"
                ]
            ]
        }

        if (events.isEmpty()) return

        def body = JsonOutput.toJson([events: events, deviceId: DEVICE_ID])
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", body)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
    }
}
