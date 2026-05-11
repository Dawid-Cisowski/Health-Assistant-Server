package com.healthassistant

import groovy.json.JsonOutput
import org.springframework.ai.chat.model.ToolContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import spock.lang.Title

/**
 * Integration tests for AI Assistant workout routine management tools.
 * Tests tool methods directly via Spring ApplicationContext (Groovy duck typing)
 * to verify CRUD operations on workout routines through the AI tool layer.
 *
 * These tests define the contract for six new tools:
 * - getExerciseCatalog: returns all available exercises
 * - getWorkoutRoutines: returns routines for the device
 * - getWorkoutRoutineDetail: returns full routine detail by ID
 * - createWorkoutRoutine: creates a new routine with exercises
 * - updateWorkoutRoutine: updates an existing routine
 * - deleteWorkoutRoutine: deletes a routine
 */
@Title("Feature: AI Assistant Workout Routine Management Tools")
class RoutineAiToolsSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-routine-ai-tools"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    @Autowired
    ApplicationContext applicationContext

    private Object healthTools

    def setup() {
        healthTools = applicationContext.getBean("healthTools")
        cleanupEventsForDevice(DEVICE_ID)
        cleanupAllProjectionsForDevice(DEVICE_ID)
    }

    private ToolContext createToolContext() {
        return new ToolContext(Map.of("deviceId", DEVICE_ID))
    }

    private boolean isMutationSuccess(Object result) {
        return result.class.simpleName == "MutationSuccess"
    }

    private boolean isToolError(Object result) {
        return result.class.simpleName == "ToolError"
    }

    private String createRoutineViaHttp(String name, List<Map> exercises) {
        def request = JsonOutput.toJson([name: name, exercises: exercises])
        return authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/routines", request)
                .post("/v1/routines")
                .then()
                .statusCode(201)
                .extract()
                .body().jsonPath().getString("id")
    }

    // ===================== getExerciseCatalog =====================

    def "getExerciseCatalog should return non-empty list of available exercises"() {
        when: "requesting the exercise catalog"
        def result = healthTools.getExerciseCatalog(createToolContext())

        then: "result is not a ToolError"
        !isToolError(result)

        and: "result contains a non-empty list of exercises with id and name fields"
        def exercises = result.exercises()
        exercises != null
        exercises.size() > 0
        exercises.every { it.id() != null && !it.id().isEmpty() }
        exercises.every { it.name() != null && !it.name().isEmpty() }
    }

    def "getExerciseCatalog should include known exercise IDs"() {
        when: "requesting the exercise catalog"
        def result = healthTools.getExerciseCatalog(createToolContext())

        then: "catalog contains known exercise IDs"
        !isToolError(result)
        def exerciseIds = result.exercises().collect { it.id() }
        exerciseIds.contains("legs_1")
        exerciseIds.contains("chest_1")
        exerciseIds.contains("back_1")
    }

    // ===================== getWorkoutRoutines =====================

    def "getWorkoutRoutines should return empty list when no routines exist"() {
        when: "requesting routines for a device with none"
        def result = healthTools.getWorkoutRoutines(createToolContext())

        then: "result is not a ToolError"
        !isToolError(result)

        and: "result contains an empty list of routines"
        result.routines() != null
        result.routines().size() == 0
    }

    def "getWorkoutRoutines should return list of routines after creating one"() {
        given: "a routine exists for the device"
        createRoutineViaHttp("Push Day", [
                [exerciseId: "chest_1", orderIndex: 1, defaultSets: 4]
        ])

        when: "requesting routines"
        def result = healthTools.getWorkoutRoutines(createToolContext())

        then: "result contains the created routine"
        !isToolError(result)
        result.routines().size() >= 1
        result.routines().any { it.name() == "Push Day" }
    }

    // ===================== getWorkoutRoutineDetail =====================

    def "getWorkoutRoutineDetail should return routine details including exercises with names"() {
        given: "a routine exists with multiple exercises"
        def routineId = createRoutineViaHttp("Legs Day", [
                [exerciseId: "legs_1", orderIndex: 1, defaultSets: 5, notes: "Heavy"],
                [exerciseId: "chest_1", orderIndex: 2, defaultSets: 4]
        ])

        when: "requesting the routine detail"
        def result = healthTools.getWorkoutRoutineDetail(routineId, createToolContext())

        then: "result is not a ToolError"
        !isToolError(result)

        and: "result contains routine details with exercises and exercise names"
        result.name() == "Legs Day"
        result.exercises().size() == 2
        result.exercises().every { it.exerciseName() != null && !it.exerciseName().isEmpty() }
    }

    def "getWorkoutRoutineDetail should return ToolError for invalid UUID format"() {
        when: "requesting a routine with invalid UUID"
        def result = healthTools.getWorkoutRoutineDetail("not-a-valid-uuid", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
    }

    def "getWorkoutRoutineDetail should return ToolError for non-existent UUID"() {
        when: "requesting a routine with a valid but non-existent UUID"
        def result = healthTools.getWorkoutRoutineDetail("00000000-0000-0000-0000-000000000000", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
    }

    // ===================== createWorkoutRoutine =====================

    def "createWorkoutRoutine should create routine and return MutationSuccess with routineId"() {
        given: "valid exercise JSON"
        def exercisesJson = JsonOutput.toJson([
                [exerciseId: "chest_1", orderIndex: 1, defaultSets: 4, notes: "Tempo 3010"],
                [exerciseId: "back_2", orderIndex: 2, defaultSets: 3]
        ])

        when: "creating a routine through the tool"
        def result = healthTools.createWorkoutRoutine(
                "FBW A",
                "Full Body Workout - variant A",
                "bg-blue-500",
                exercisesJson,
                createToolContext()
        )

        then: "result is a MutationSuccess with routineId"
        isMutationSuccess(result)
        result.data()?.routineId() != null
    }

    def "createWorkoutRoutine should create routine that is retrievable via getWorkoutRoutineDetail"() {
        given: "valid exercise JSON"
        def exercisesJson = JsonOutput.toJson([
                [exerciseId: "legs_1", orderIndex: 1, defaultSets: 5]
        ])

        when: "creating a routine through the tool"
        def createResult = healthTools.createWorkoutRoutine(
                "Leg Day",
                "Heavy leg session",
                null,
                exercisesJson,
                createToolContext()
        )

        then: "routine is created"
        isMutationSuccess(createResult)
        def routineId = createResult.data().routineId()

        when: "fetching the created routine"
        def detailResult = healthTools.getWorkoutRoutineDetail(routineId, createToolContext())

        then: "routine details match what was created"
        !isToolError(detailResult)
        detailResult.name() == "Leg Day"
        detailResult.exercises().size() == 1
        detailResult.exercises()[0].exerciseId() == "legs_1"
    }

    def "createWorkoutRoutine should return ToolError for invalid exercisesJson"() {
        when: "creating a routine with malformed JSON"
        def result = healthTools.createWorkoutRoutine(
                "Bad Routine",
                null,
                null,
                "this is not valid json",
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
    }

    def "createWorkoutRoutine should return ToolError for invalid exerciseId"() {
        given: "exercise JSON with invalid exercise ID"
        def exercisesJson = JsonOutput.toJson([
                [exerciseId: "invalid_exercise_id", orderIndex: 1, defaultSets: 3]
        ])

        when: "creating a routine with invalid exercise ID"
        def result = healthTools.createWorkoutRoutine(
                "Bad Exercise Routine",
                null,
                null,
                exercisesJson,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
    }

    def "createWorkoutRoutine should return ToolError for empty exercises list"() {
        given: "empty exercise JSON array"
        def exercisesJson = JsonOutput.toJson([])

        when: "creating a routine with no exercises"
        def result = healthTools.createWorkoutRoutine(
                "Empty Routine",
                null,
                null,
                exercisesJson,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
    }

    def "createWorkoutRoutine should return ToolError for blank name"() {
        given: "valid exercise JSON"
        def exercisesJson = JsonOutput.toJson([
                [exerciseId: "chest_1", orderIndex: 1, defaultSets: 4]
        ])

        when: "creating a routine with blank name"
        def result = healthTools.createWorkoutRoutine(
                "   ",
                null,
                null,
                exercisesJson,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
    }

    // ===================== updateWorkoutRoutine =====================

    def "updateWorkoutRoutine should update routine and return MutationSuccess"() {
        given: "a routine exists"
        def routineId = createRoutineViaHttp("Old Name", [
                [exerciseId: "chest_1", orderIndex: 1, defaultSets: 3]
        ])

        and: "updated exercise JSON"
        def exercisesJson = JsonOutput.toJson([
                [exerciseId: "back_1", orderIndex: 1, defaultSets: 5],
                [exerciseId: "back_2", orderIndex: 2, defaultSets: 4]
        ])

        when: "updating the routine through the tool"
        def result = healthTools.updateWorkoutRoutine(
                routineId,
                "New Name",
                "Updated description",
                "bg-green-500",
                exercisesJson,
                createToolContext()
        )

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
    }

    def "updateWorkoutRoutine should persist changes visible via getWorkoutRoutineDetail"() {
        given: "a routine exists"
        def routineId = createRoutineViaHttp("Original", [
                [exerciseId: "chest_1", orderIndex: 1, defaultSets: 3]
        ])

        and: "updated exercise JSON"
        def exercisesJson = JsonOutput.toJson([
                [exerciseId: "legs_1", orderIndex: 1, defaultSets: 5]
        ])

        when: "updating the routine"
        def updateResult = healthTools.updateWorkoutRoutine(
                routineId,
                "Updated Name",
                "New description",
                "bg-red-500",
                exercisesJson,
                createToolContext()
        )

        then: "update is successful"
        isMutationSuccess(updateResult)

        when: "fetching the updated routine"
        def detailResult = healthTools.getWorkoutRoutineDetail(routineId, createToolContext())

        then: "routine reflects the updates"
        !isToolError(detailResult)
        detailResult.name() == "Updated Name"
        detailResult.exercises().size() == 1
        detailResult.exercises()[0].exerciseId() == "legs_1"
    }

    def "updateWorkoutRoutine should return ToolError for non-existent routineId"() {
        given: "exercise JSON"
        def exercisesJson = JsonOutput.toJson([
                [exerciseId: "chest_1", orderIndex: 1, defaultSets: 3]
        ])

        when: "updating a non-existent routine"
        def result = healthTools.updateWorkoutRoutine(
                "00000000-0000-0000-0000-000000000000",
                "Name",
                null,
                null,
                exercisesJson,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
    }

    def "updateWorkoutRoutine should return ToolError for invalid UUID format"() {
        given: "exercise JSON"
        def exercisesJson = JsonOutput.toJson([
                [exerciseId: "chest_1", orderIndex: 1, defaultSets: 3]
        ])

        when: "updating with invalid UUID"
        def result = healthTools.updateWorkoutRoutine(
                "not-a-valid-uuid",
                "Name",
                null,
                null,
                exercisesJson,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
    }

    // ===================== deleteWorkoutRoutine =====================

    def "deleteWorkoutRoutine should delete routine and return MutationSuccess"() {
        given: "a routine exists"
        def routineId = createRoutineViaHttp("To Delete", [
                [exerciseId: "chest_1", orderIndex: 1, defaultSets: 3]
        ])

        when: "deleting the routine through the tool"
        def result = healthTools.deleteWorkoutRoutine(routineId, createToolContext())

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
    }

    def "deleteWorkoutRoutine should make routine inaccessible via getWorkoutRoutineDetail"() {
        given: "a routine exists"
        def routineId = createRoutineViaHttp("Will Be Deleted", [
                [exerciseId: "chest_1", orderIndex: 1, defaultSets: 3]
        ])

        when: "deleting the routine"
        def deleteResult = healthTools.deleteWorkoutRoutine(routineId, createToolContext())

        then: "deletion is successful"
        isMutationSuccess(deleteResult)

        when: "trying to get the deleted routine"
        def detailResult = healthTools.getWorkoutRoutineDetail(routineId, createToolContext())

        then: "result is a ToolError (not found)"
        isToolError(detailResult)
    }

    def "deleteWorkoutRoutine should return ToolError for non-existent routineId"() {
        when: "deleting a non-existent routine"
        def result = healthTools.deleteWorkoutRoutine("00000000-0000-0000-0000-000000000000", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
    }

    def "deleteWorkoutRoutine should return ToolError for invalid UUID format"() {
        when: "deleting with invalid UUID"
        def result = healthTools.deleteWorkoutRoutine("not-a-valid-uuid", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
    }

    // ===================== Full CRUD flow =====================

    def "full routine flow: create via tool, list, get detail, update, delete, verify gone"() {
        given: "valid exercise JSON for creation"
        def exercisesJson = JsonOutput.toJson([
                [exerciseId: "chest_1", orderIndex: 1, defaultSets: 4],
                [exerciseId: "back_2", orderIndex: 2, defaultSets: 3]
        ])

        when: "creating a routine"
        def createResult = healthTools.createWorkoutRoutine(
                "Full Flow Routine",
                "Testing full CRUD",
                "bg-purple-500",
                exercisesJson,
                createToolContext()
        )

        then: "routine is created"
        isMutationSuccess(createResult)
        def routineId = createResult.data().routineId()
        routineId != null

        when: "listing routines"
        def listResult = healthTools.getWorkoutRoutines(createToolContext())

        then: "the created routine appears in the list"
        !isToolError(listResult)
        listResult.routines().any { it.name() == "Full Flow Routine" }

        when: "getting routine detail"
        def detailResult = healthTools.getWorkoutRoutineDetail(routineId, createToolContext())

        then: "detail matches what was created"
        !isToolError(detailResult)
        detailResult.name() == "Full Flow Routine"
        detailResult.exercises().size() == 2

        when: "updating the routine"
        def updatedExercisesJson = JsonOutput.toJson([
                [exerciseId: "legs_1", orderIndex: 1, defaultSets: 5]
        ])
        def updateResult = healthTools.updateWorkoutRoutine(
                routineId,
                "Updated Flow Routine",
                "Updated description",
                "bg-orange-500",
                updatedExercisesJson,
                createToolContext()
        )

        then: "update is successful"
        isMutationSuccess(updateResult)

        when: "verifying the update"
        def updatedDetail = healthTools.getWorkoutRoutineDetail(routineId, createToolContext())

        then: "routine reflects updates"
        !isToolError(updatedDetail)
        updatedDetail.name() == "Updated Flow Routine"
        updatedDetail.exercises().size() == 1
        updatedDetail.exercises()[0].exerciseId() == "legs_1"

        when: "deleting the routine"
        def deleteResult = healthTools.deleteWorkoutRoutine(routineId, createToolContext())

        then: "deletion is successful"
        isMutationSuccess(deleteResult)

        when: "trying to get the deleted routine"
        def deletedDetail = healthTools.getWorkoutRoutineDetail(routineId, createToolContext())

        then: "routine is gone"
        isToolError(deletedDetail)
    }
}
