package com.healthassistant.weightimport;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.weightimport.api.WeightImportFacade;
import com.healthassistant.weightimport.api.dto.WeightImportResponse;
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

@RestController
@RequestMapping("/v1/weight")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Weight Import", description = "Import weight data from smart scale screenshots")
class WeightImportController {

    private final WeightImportFacade weightImportFacade;

    @PostMapping(value = "/import-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Import weight from smart scale screenshots",
            description = """
                    Upload one or more smart scale app screenshots (e.g., scrolled views of the same measurement).
                    AI extracts body composition data including weight, BMI, body fat %, muscle %,
                    hydration, BMR, visceral fat level, and more from all provided images.
                    Creates a WeightMeasurementRecorded.v1 event. If a measurement with the same
                    timestamp already exists, it will be updated.
                    """,
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Weight extraction processed (check status field)"),
            @ApiResponse(responseCode = "400", description = "Invalid image file"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<WeightImportResponse> importWeightFromImage(
            @RequestParam("images") java.util.List<MultipartFile> images,
            @RequestAttribute("deviceId") String deviceId
    ) {
        log.info("Weight image import request from device {}, images count: {}, total size: {} bytes",
                WeightImportSecurityUtils.maskDeviceId(deviceId),
                images.size(),
                images.stream().mapToLong(MultipartFile::getSize).sum());

        try {
            WeightImportResponse response = weightImportFacade.importFromImages(
                    images, DeviceId.of(deviceId)
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid images from device {}: {}", WeightImportSecurityUtils.maskDeviceId(deviceId), e.getMessage());
            return ResponseEntity.badRequest().body(WeightImportResponse.failure(e.getMessage()));
        }
    }
}
