package com.healthassistant.mealimport;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.mealimport.api.MealImportFacade;
import com.healthassistant.mealimport.api.dto.MealImportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/v1/meals")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Meal Import", description = "Import meals from descriptions and/or images")
class MealImportController {

    private final MealImportFacade mealImportFacade;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Import meal from description and/or photos",
        description = "Upload a text description and/or photos of a meal. AI analyzes the content, " +
            "estimates nutritional values, and creates a MealRecorded.v1 event. " +
            "At least one of description or images is required.",
        security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Meal extraction processed (check status field)"),
        @ApiResponse(responseCode = "400", description = "Invalid input (no description or images, invalid image type)"),
        @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<MealImportResponse> importMeal(
        @RequestParam(value = "description", required = false) String description,
        @RequestParam(value = "images", required = false) List<MultipartFile> images,
        @RequestAttribute("deviceId") String deviceId
    ) {
        log.info("Meal import request from device {}, description: {}, images: {}",
            deviceId,
            description != null ? description.length() + " chars" : "none",
            images != null ? images.size() : 0);

        try {
            MealImportResponse response = mealImportFacade.importMeal(
                description, images, DeviceId.of(deviceId)
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid meal import from device {}: {}", deviceId, e.getMessage());
            return ResponseEntity.badRequest().body(MealImportResponse.failure(e.getMessage()));
        }
    }
}
