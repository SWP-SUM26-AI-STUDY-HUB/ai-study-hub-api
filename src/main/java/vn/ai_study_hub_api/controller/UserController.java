package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.response.UserResponse;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.ProfileService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "User Profile", description = "Endpoints for user profile management")
public class UserController {

    private final ProfileService profileService;

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get current user profile", description = "Returns the authenticated user's profile information")
    public ApiResponse<UserResponse> getMyProfile() {
        UUID userId = getCurrentUserId();
        UserResponse response = profileService.getMyProfile(userId);
        return ApiResponse.success(response, "Profile retrieved successfully.");
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getId();
    }
}
