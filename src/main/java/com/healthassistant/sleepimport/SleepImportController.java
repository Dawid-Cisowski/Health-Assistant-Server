package com.healthassistant.sleepimport;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.sleepimport.api.SleepImportFacade;
import com.healthassistant.sleepimport.api.dto.SleepImportResponse;
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
@RequestMapping("/v1/sleep")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Sleep Import", description = "Import sleep data from ohealth screenshots")
class SleepImportController {

    private final SleepImportFacade sleepImportFacade;

    @PostMapping(value = "/import-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Import sleep from ohealth screenshot",
            description = """
                    Upload an ohealth sleep summary screenshot. AI extracts sleep data including phases
                    and creates a SleepSessionRecorded.v1 event. If a sleep record with the same start
                    time already exists, it will be overwritten with the new data.
                    """,
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sleep extraction processed (check status field)"),
            @ApiResponse(responseCode = "400", description = "Invalid image file"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<SleepImportResponse> importSleepFromImage(
            @RequestParam("image") MultipartFile image,
            @RequestAttribute("deviceId") String deviceId
    ) {
        log.info("Sleep image import request from device {}, file: {}, size: {} bytes",
                deviceId, image.getOriginalFilename(), image.getSize());

        try {
            SleepImportResponse response = sleepImportFacade.importFromImage(
                    image, DeviceId.of(deviceId)
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid image from device {}: {}", deviceId, e.getMessage());
            return ResponseEntity.badRequest().body(SleepImportResponse.failure(e.getMessage()));
        }
    }
}
