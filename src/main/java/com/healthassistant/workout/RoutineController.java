package com.healthassistant.workout;

import com.healthassistant.workout.api.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/routines")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Routines", description = "Workout routine (training plan) management endpoints")
class RoutineController {

    private final RoutineService routineService;

    @GetMapping
    @Operation(
            summary = "Get all routines",
            description = "Returns all workout routines for the authenticated device",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponse(responseCode = "200", description = "Routines retrieved successfully")
    ResponseEntity<List<RoutineListResponse>> getRoutines(
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Retrieving routines for device: {}", deviceId);
        List<RoutineListResponse> routines = routineService.getRoutines(deviceId);
        return ResponseEntity.ok(routines);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get routine details",
            description = "Returns detailed information about a specific routine including exercises",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Routine found"),
            @ApiResponse(responseCode = "404", description = "Routine not found")
    })
    ResponseEntity<RoutineResponse> getRoutine(
            @PathVariable UUID id,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Retrieving routine {} for device: {}", id, deviceId);
        return routineService.getRoutine(id, deviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(
            summary = "Create routine",
            description = "Creates a new workout routine with exercises",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Routine created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    ResponseEntity<RoutineResponse> createRoutine(
            @Valid @RequestBody RoutineRequest request,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Creating routine '{}' for device: {}", request.name(), deviceId);
        RoutineResponse response = routineService.createRoutine(request, deviceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update routine",
            description = "Updates an existing routine with new data",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Routine updated successfully"),
            @ApiResponse(responseCode = "404", description = "Routine not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    ResponseEntity<RoutineResponse> updateRoutine(
            @PathVariable UUID id,
            @Valid @RequestBody RoutineRequest request,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Updating routine {} for device: {}", id, deviceId);
        return routineService.updateRoutine(id, request, deviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete routine",
            description = "Deletes a workout routine",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Routine deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Routine not found")
    })
    ResponseEntity<Void> deleteRoutine(
            @PathVariable UUID id,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Deleting routine {} for device: {}", id, deviceId);
        boolean deleted = routineService.deleteRoutine(id, deviceId);
        return deleted
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
