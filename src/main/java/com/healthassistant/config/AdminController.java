package com.healthassistant.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Administrative operations for data management")
class AdminController {

    private final AdminService adminService;

    @DeleteMapping("/all-data")
    @Operation(
            summary = "Delete all data from database",
            description = "Deletes all data from the database including health events, projections, summaries, and sync state. This operation is irreversible and intended for testing/development purposes only. Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "All data deleted successfully"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "500", description = "Error occurred while deleting data")
    })
    ResponseEntity<Void> deleteAllData() {
        log.warn("DELETE /v1/admin/all-data endpoint called - deleting all data");
        adminService.deleteAllData();
        return ResponseEntity.noContent().build();
    }
}
