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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.response.UserResponse;
import vn.ai_study_hub_api.model.UserRole;
import vn.ai_study_hub_api.model.UserStatus;
import vn.ai_study_hub_api.service.UserService;
import vn.ai_study_hub_api.service.UserSanctionService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Users", description = "Endpoints for administrator user management (warn/ban)")
public class AdminUserController {

    private final UserSanctionService userSanctionService;
    private final UserService userService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get user list with filtering and pagination", description = "Returns a paginated list of users filtered by search keyword, role, and status.")
    public vn.ai_study_hub_api.controller.response.UserPageResponse getUsers(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "role", required = false) UserRole role,
            @RequestParam(value = "status", required = false) UserStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        log.info("Admin request to get users list. Search: {}, Role: {}, Status: {}, Page: {}, Size: {}", search, role, status, page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<UserResponse> users = userService.getUsers(search, role, status, pageable);
        vn.ai_study_hub_api.controller.response.UserPageResponse pageResponse = new vn.ai_study_hub_api.controller.response.UserPageResponse();
        pageResponse.setSuccess(true);
        pageResponse.setMessage("User list retrieved successfully.");
        pageResponse.setData(users);
        return pageResponse;
    }

    @PostMapping("/{userId}/ban")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Ban a user account", description = "Updates user status to BANNED, revokes refresh token, and blacklists all active access tokens.")
    public ApiResponse<Void> banUser(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody(required = false) BanRequest request) {
        log.info("Admin request to ban user ID: {}", userId);
        if (request != null && request.getReason() != null && !request.getReason().isBlank()) {
            userSanctionService.banUser(userId, request.getReason());
        } else {
            userSanctionService.banUser(userId);
        }
        return ApiResponse.success("User banned successfully and all active sessions terminated.");
    }

    @PostMapping("/{userId}/reactivate")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Reactivate a banned user account", description = "Updates user status back to ACTIVE and logs the activation history.")
    public ApiResponse<Void> reactivateUser(@PathVariable("userId") UUID userId) {
        log.info("Admin request to reactivate user ID: {}", userId);
        userSanctionService.reactivateUser(userId);
        return ApiResponse.success("User account reactivated successfully.");
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

    @Getter
    @Setter
    public static class BanRequest {
        @Size(max = 1000, message = "Ban reason must not exceed 1000 characters.")
        private String reason;
    }
}
