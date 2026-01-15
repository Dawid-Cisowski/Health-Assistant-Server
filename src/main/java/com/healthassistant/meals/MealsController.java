package com.healthassistant.meals;

import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.meals.api.dto.MealDailyDetailResponse;
import com.healthassistant.meals.api.dto.MealResponse;
import com.healthassistant.meals.api.dto.MealsRangeSummaryResponse;
import com.healthassistant.meals.api.dto.RecordMealRequest;
import com.healthassistant.meals.api.dto.UpdateMealRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/v1/meals")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Meals", description = "Meal tracking and analytics endpoints")
class MealsController {

    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,128}$");
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,100}$");

    private final MealsFacade mealsFacade;

    @GetMapping("/daily/{date}")
    @Operation(
            summary = "Get daily meal detail",
            description = "Retrieves all meals for a specific date with nutritional metrics",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Daily meal detail retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<MealDailyDetailResponse> getDailyDetail(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate date,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        validateDeviceId(deviceId);
        log.info("Retrieving daily meal detail for device {} and date: {}", sanitizeForLog(deviceId), date);
        MealDailyDetailResponse detail = mealsFacade.getDailyDetail(deviceId, date);
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/range")
    @Operation(
            summary = "Get meals range summary",
            description = "Retrieves aggregated meal data for a date range with daily breakdown",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Range summary retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid date range or request parameters"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<MealsRangeSummaryResponse> getRangeSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate endDate,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        validateDeviceId(deviceId);

        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start {} is after end {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        LocalDate today = LocalDate.now();
        LocalDate effectiveEndDate = endDate.isAfter(today) ? today : endDate;

        log.info("Retrieving meals range summary for device {} from {} to {}", sanitizeForLog(deviceId), startDate, effectiveEndDate);
        MealsRangeSummaryResponse summary = mealsFacade.getRangeSummary(deviceId, startDate, effectiveEndDate);
        return ResponseEntity.ok(summary);
    }

    @PostMapping
    @Operation(
            summary = "Record a new meal",
            description = "Creates a new meal record. Can optionally specify occurredAt for backdating (up to 30 days).",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Meal recorded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters or backdating validation failed"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<MealResponse> recordMeal(
            @Valid @RequestBody RecordMealRequest request,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        validateDeviceId(deviceId);
        log.info("Recording meal for device {}", sanitizeForLog(deviceId));
        MealResponse response = mealsFacade.recordMeal(deviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{eventId}")
    @Operation(
            summary = "Delete a meal",
            description = "Deletes an existing meal record by its event ID",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Meal deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "404", description = "Meal not found")
    })
    ResponseEntity<Void> deleteMeal(
            @PathVariable @NotBlank String eventId,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        validateDeviceId(deviceId);
        validateEventId(eventId);
        log.info("Deleting meal {} for device {}", sanitizeForLog(eventId), sanitizeForLog(deviceId));
        mealsFacade.deleteMeal(deviceId, eventId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{eventId}")
    @Operation(
            summary = "Update a meal",
            description = "Updates an existing meal record. All fields are required. Can optionally change occurredAt (up to 30 days in the past).",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Meal updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters or backdating validation failed"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "404", description = "Meal not found")
    })
    ResponseEntity<MealResponse> updateMeal(
            @PathVariable @NotBlank String eventId,
            @Valid @RequestBody UpdateMealRequest request,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        validateDeviceId(deviceId);
        validateEventId(eventId);
        log.info("Updating meal {} for device {}", sanitizeForLog(eventId), sanitizeForLog(deviceId));
        MealResponse response = mealsFacade.updateMeal(deviceId, eventId, request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(MealNotFoundException.class)
    ResponseEntity<Void> handleMealNotFound(MealNotFoundException ex) {
        log.warn("Meal not found");
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(BackdatingValidationException.class)
    ResponseEntity<String> handleBackdatingValidation(BackdatingValidationException ex) {
        log.warn("Backdating validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    private void validateDeviceId(String deviceId) {
        if (deviceId == null || !DEVICE_ID_PATTERN.matcher(deviceId).matches()) {
            throw new IllegalArgumentException("Invalid device ID format");
        }
    }

    private void validateEventId(String eventId) {
        if (eventId == null || !EVENT_ID_PATTERN.matcher(eventId).matches()) {
            throw new IllegalArgumentException("Invalid event ID format");
        }
    }

    private String sanitizeForLog(String value) {
        if (value == null) return "null";
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
