package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.service.UserSanctionService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Users", description = "Endpoints for administrator user management (warn/ban)")
public class AdminUserController {

    private final UserSanctionService userSanctionService;

    @PostMapping("/{userId}/ban")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Ban a user account", description = "Updates user status to BANNED, revokes refresh token, and blacklists all active access tokens.")
    public ApiResponse<Void> banUser(@PathVariable("userId") UUID userId) {
        log.info("Admin request to ban user ID: {}", userId);
        userSanctionService.banUser(userId);
        return ApiResponse.success("User banned successfully and all active sessions terminated.");
    }

    @PostMapping("/{userId}/warn")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Warn a user", description = "Logs a violation history record and sends a warning notification to the user.")
    public ApiResponse<Void> warnUser(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody WarnRequest request) {
        log.info("Admin request to warn user ID: {} with reason: {}", userId, request.getReason());
        userSanctionService.warnUser(userId, request.getReason());
        return ApiResponse.success("Warning issued to the user successfully.");
    }

    @Getter
    @Setter
    public static class WarnRequest {
        @NotBlank(message = "Warning reason is required.")
        @Size(max = 1000, message = "Warning reason must not exceed 1000 characters.")
        private String reason;
    }
}
