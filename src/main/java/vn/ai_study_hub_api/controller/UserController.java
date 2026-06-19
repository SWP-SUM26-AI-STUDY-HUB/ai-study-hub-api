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
import vn.ai_study_hub_api.service.UserService;
import vn.ai_study_hub_api.service.AuthService;
import vn.ai_study_hub_api.controller.request.ChangePasswordRequest;
import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "User Profile", description = "Endpoints for user profile management")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @GetMapping("/profile")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get current user profile", description = "Returns the authenticated user's profile information")
    public ApiResponse<UserResponse> getMyProfile() {
        UUID userId = getCurrentUserId();
        UserResponse response = userService.getMyProfile(userId);
        return ApiResponse.success(response, "Profile retrieved successfully.");
    }

    @PutMapping(value = "/edit-profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Update user profile", description = "Updates the authenticated user's display name, bio, and/or avatar. Only JPEG/PNG files under 2MB are accepted.")
    public ApiResponse<UserResponse> updateProfile(
            @RequestPart(value = "fullName", required = false)
            @Size(max = 100, message = "Full name must not exceed 100 characters")
            String fullName,
            @RequestPart(value = "bio", required = false)
            @Size(max = 255, message = "Bio must not exceed 255 characters")
            String bio,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar) {
        UUID userId = getCurrentUserId();
        UserResponse response = userService.updateProfile(userId, fullName, bio, avatar);
        return ApiResponse.success(response, "Profile updated successfully.");
    }


    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Change password", description = "Allows authenticated user to change their password by validating the current password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = getCurrentUserId();
        authService.changePassword(userId, request);
        return ApiResponse.success("Password changed successfully.");
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getId();
    }
}
