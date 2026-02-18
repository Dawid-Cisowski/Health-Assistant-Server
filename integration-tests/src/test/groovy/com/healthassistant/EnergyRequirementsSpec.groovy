package com.healthassistant

import spock.lang.Title

import java.time.LocalDate

@Title("Feature: Energy Requirements and Macro Targets API")
class EnergyRequirementsSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-energy"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final LocalDate TEST_DATE = LocalDate.of(2025, 1, 15)

    def setup() {
        cleanupAllProjectionsForDevice(DEVICE_ID)
    }

    def "Scenario 1: Calculate energy requirements with 3 LBM measurements (averaging)"() {
        given: "3 weight measurements with LBM data"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        def request = createWeightEventsWithLbm([57.0, 57.4, 57.8], uuid)

        when: "I submit weight events"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "I request energy requirements"
        def response = waitForApiResponse("/v1/meals/energy-requirements/${TEST_DATE}", DEVICE_ID, SECRET_BASE64)

        then: "effective LBM is averaged from 3 measurements"
        response.getInt("lbmMeasurementsUsed") == 3
        response.get("effectiveLbmKg") == 57.4f

        and: "BMR is calculated correctly (370 + 21.6 × 57.4 = 1610)"
        response.getInt("bmrKcal") == 1610

        and: "base calories are BMR × 1.35"
        response.getInt("baseKcal") == 2174

        and: "surplus is 300 kcal"
        response.getInt("surplusKcal") == 300
    }

    def "Scenario 2: Fallback to single LBM measurement when only one available"() {
        given: "only 1 weight measurement"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        def request = createSingleWeightEvent(57.4, uuid)

        when: "I submit weight event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "I request energy requirements"
        def response = waitForApiResponse("/v1/meals/energy-requirements/${TEST_DATE}", DEVICE_ID, SECRET_BASE64)

        then: "only 1 measurement is used"
        response.getInt("lbmMeasurementsUsed") == 1
        response.get("effectiveLbmKg") == 57.4f
    }

    def "Scenario 3: Rest day has fat target of 80g"() {
        given: "weight data exists"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        submitWeightEvents([57.4], uuid)

        when: "I request energy requirements"
        def response = waitForApiResponse("/v1/meals/energy-requirements/${TEST_DATE}", DEVICE_ID, SECRET_BASE64)

        then: "it's not a training day"
        response.getBoolean("isTrainingDay") == false
        response.getInt("trainingBonusKcal") == 0

        and: "fat target is 80g for rest day"
        response.getInt("macroTargets.fatGrams") == 80
    }

    def "Scenario 4: Training day has fat 50g and +250 kcal bonus"() {
        given: "weight data exists"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        submitWeightEvents([57.4], uuid)

        and: "a workout on the date"
        submitWorkoutEvent(uuid)

        when: "I request energy requirements"
        def response = waitForApiResponse("/v1/meals/energy-requirements/${TEST_DATE}", DEVICE_ID, SECRET_BASE64)

        then: "it's a training day"
        response.getBoolean("isTrainingDay") == true
        response.getInt("trainingBonusKcal") == 250

        and: "fat target is 50g for training day"
        response.getInt("macroTargets.fatGrams") == 50
    }

    def "Scenario 5: Steps below 4000 threshold give 0 step bonus"() {
        given: "weight data exists"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        submitWeightEvents([57.4], uuid)

        and: "steps below threshold"
        submitStepsEvent(3000, uuid)

        when: "I request energy requirements"
        def response = waitForApiResponse("/v1/meals/energy-requirements/${TEST_DATE}", DEVICE_ID, SECRET_BASE64)

        then: "step intervals is 0"
        response.getInt("stepIntervals") == 0
        response.getInt("stepBonusKcal") == 0
    }

    def "Scenario 6: Steps of 8000 give 2 intervals (180 kcal bonus)"() {
        given: "weight data exists"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        submitWeightEvents([57.4], uuid)

        and: "8000 steps"
        submitStepsEvent(8000, uuid)

        when: "I request energy requirements"
        def response = waitForApiResponse("/v1/meals/energy-requirements/${TEST_DATE}", DEVICE_ID, SECRET_BASE64)

        then: "floor((8000-4000)/2000) = 2 intervals"
        response.getInt("steps") == 8000
        response.getInt("stepIntervals") == 2
        response.getInt("stepBonusKcal") == 180
    }

    def "Scenario 7: Steps are capped at 8 intervals max (even with 22000+ steps)"() {
        given: "weight data exists"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        submitWeightEvents([57.4], uuid)

        and: "22000 steps (would be 9 intervals uncapped)"
        submitStepsEvent(22000, uuid)

        when: "I request energy requirements"
        def response = waitForApiResponse("/v1/meals/energy-requirements/${TEST_DATE}", DEVICE_ID, SECRET_BASE64)

        then: "intervals are capped at 8"
        response.getInt("stepIntervals") == 8
        response.getInt("stepBonusKcal") == 720
    }

    def "Scenario 8: Full calculation for training day with 8000 steps"() {
        given: "weight data with LBM 57.4"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        submitWeightEvents([57.4], uuid)

        and: "8000 steps"
        submitStepsEvent(8000, uuid)

        and: "a workout"
        submitWorkoutEvent(uuid)

        when: "I request energy requirements"
        def response = waitForApiResponse("/v1/meals/energy-requirements/${TEST_DATE}", DEVICE_ID, SECRET_BASE64)

        then: "all values are calculated correctly"
        response.getInt("bmrKcal") == 1610
        response.getInt("baseKcal") == 2174
        response.getInt("stepBonusKcal") == 180
        response.getInt("trainingBonusKcal") == 250
        response.getInt("surplusKcal") == 300
        response.getInt("targetCaloriesKcal") == 2904

        and: "macros are calculated correctly"
        response.getInt("macroTargets.proteinGrams") == 161
        response.getInt("macroTargets.fatGrams") == 50
        response.getInt("macroTargets.carbsGrams") == 452
    }

    def "Scenario 9: Consumed and remaining nutrition calculated from meals"() {
        given: "weight data"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        submitWeightEvents([57.4], uuid)

        and: "some meals consumed"
        submitMealEvent(uuid)

        when: "I request energy requirements"
        def response = waitForApiResponse("/v1/meals/energy-requirements/${TEST_DATE}", DEVICE_ID, SECRET_BASE64)

        then: "consumed nutrition is returned"
        response.getInt("consumed.caloriesKcal") == 800
        response.getInt("consumed.proteinGrams") == 50
        response.getInt("consumed.fatGrams") == 25
        response.getInt("consumed.carbsGrams") == 80

        and: "remaining nutrition is calculated"
        def targetCalories = response.getInt("targetCaloriesKcal")
        response.getInt("remaining.caloriesKcal") == targetCalories - 800
    }

    def "Scenario 10: No meals consumed returns null for consumed/remaining"() {
        given: "weight data only, no meals"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        submitWeightEvents([57.4], uuid)

        when: "I request energy requirements"
        def response = waitForApiResponse("/v1/meals/energy-requirements/${TEST_DATE}", DEVICE_ID, SECRET_BASE64)

        then: "consumed and remaining are null"
        response.get("consumed") == null
        response.get("remaining") == null
    }

    def "Scenario 11: No weight data returns 400 Bad Request"() {
        when: "I request energy requirements with no weight data"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/energy-requirements/${TEST_DATE}")
                .get("/v1/meals/energy-requirements/${TEST_DATE}")
                .then()
                .extract()

        then: "400 Bad Request is returned"
        response.statusCode() == 400
    }

    def "Scenario 12: Protein is calculated as 2.8 × LBM"() {
        given: "weight data with LBM 60.0"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        submitWeightEvents([60.0], uuid)

        when: "I request energy requirements"
        def response = waitForApiResponse("/v1/meals/energy-requirements/${TEST_DATE}", DEVICE_ID, SECRET_BASE64)

        then: "protein = 2.8 × 60 = 168"
        response.getInt("macroTargets.proteinGrams") == 168
    }

    private void submitWeightEvents(List<BigDecimal> lbmValues, String uuid) {
        def request = createWeightEventsWithLbm(lbmValues, uuid)
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
    }

    private void submitStepsEvent(int steps, String uuid) {
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|steps|${TEST_DATE}-${uuid}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "${TEST_DATE}T23:00:00Z",
                "payload": {
                    "bucketStart": "${TEST_DATE}T00:00:00Z",
                    "bucketEnd": "${TEST_DATE}T23:59:59Z",
                    "count": ${steps},
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
    }

    private void submitWorkoutEvent(String uuid) {
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|workout|${TEST_DATE}-${uuid}",
                "type": "WorkoutRecorded.v1",
                "occurredAt": "${TEST_DATE}T18:00:00Z",
                "payload": {
                    "workoutId": "workout-${TEST_DATE}-${uuid}",
                    "performedAt": "${TEST_DATE}T17:00:00Z",
                    "source": "STRONG",
                    "exercises": [{
                        "exerciseId": "bench-press",
                        "name": "Bench Press",
                        "orderInWorkout": 1,
                        "sets": [{
                            "setNumber": 1,
                            "weightKg": 80.0,
                            "reps": 8,
                            "isWarmup": false
                        }]
                    }]
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
    }

    private void submitMealEvent(String uuid) {
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|meal|${TEST_DATE}-${uuid}",
                "type": "MealRecorded.v1",
                "occurredAt": "${TEST_DATE}T12:00:00Z",
                "payload": {
                    "title": "Lunch",
                    "mealType": "LUNCH",
                    "caloriesKcal": 800,
                    "proteinGrams": 50,
                    "fatGrams": 25,
                    "carbohydratesGrams": 80,
                    "healthRating": "HEALTHY"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
    }

    private String createWeightEventsWithLbm(List<BigDecimal> lbmValues, String uuid) {
        def events = []
        lbmValues.eachWithIndex { lbm, idx ->
            def date = TEST_DATE.minusDays(lbmValues.size() - 1 - idx)
            events << """
            {
                "idempotencyKey": "${DEVICE_ID}|weight|${date}-${uuid}-${idx}",
                "type": "WeightMeasurementRecorded.v1",
                "occurredAt": "${date}T08:00:00Z",
                "payload": {
                    "measurementId": "weight-${date}-${uuid}-${idx}",
                    "measuredAt": "${date}T07:30:00Z",
                    "weightKg": 72.6,
                    "leanBodyMassKg": ${lbm},
                    "bodyFatPercent": 21.0,
                    "source": "SCALE_SCREENSHOT"
                }
            }
            """
        }
        return """
        {
            "events": [${events.join(',')}],
            "deviceId": "${DEVICE_ID}"
        }
        """
    }

    private String createSingleWeightEvent(BigDecimal lbm, String uuid) {
        return """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|weight|${TEST_DATE}-${uuid}",
                "type": "WeightMeasurementRecorded.v1",
                "occurredAt": "${TEST_DATE}T08:00:00Z",
                "payload": {
                    "measurementId": "weight-${TEST_DATE}-${uuid}",
                    "measuredAt": "${TEST_DATE}T07:30:00Z",
                    "weightKg": 72.6,
                    "leanBodyMassKg": ${lbm},
                    "bodyFatPercent": 21.0,
                    "source": "SCALE_SCREENSHOT"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """
    }
}
