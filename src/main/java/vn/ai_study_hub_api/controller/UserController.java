package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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

    @PutMapping(value = "/me/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Update user profile", description = "Updates the authenticated user's display name and/or avatar. Only JPEG/PNG files under 2MB are accepted.")
    public ApiResponse<UserResponse> updateProfile(
            @RequestPart(value = "fullName", required = false)
            @Size(max = 100, message = "Full name must not exceed 100 characters")
            String fullName,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar) {
        UUID userId = getCurrentUserId();
        UserResponse response = profileService.updateProfile(userId, fullName, avatar);
        return ApiResponse.success(response, "Profile updated successfully.");
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getId();
    }
}
