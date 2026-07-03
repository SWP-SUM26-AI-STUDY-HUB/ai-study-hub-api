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
import vn.ai_study_hub_api.controller.response.UserStorageResponse;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.UserService;
import vn.ai_study_hub_api.service.AuthService;
import vn.ai_study_hub_api.controller.request.ChangePasswordRequest;
import vn.ai_study_hub_api.controller.request.UpdateProfileRequest;
import vn.ai_study_hub_api.controller.request.SavePreferredTagsRequest;
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

    @GetMapping("/storage")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get current user storage usage", description = "Returns details about the user's current storage usage, total limit, and subscription plan")
    public ApiResponse<UserStorageResponse> getUserStorage() {
        UUID userId = getCurrentUserId();
        UserStorageResponse response = userService.getUserStorage(userId);
        return ApiResponse.success(response, "Storage usage retrieved successfully.");
    }

    @PutMapping(value = "/edit-profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Update user profile", description = "Updates the authenticated user's display name and/or bio.")
    public ApiResponse<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = getCurrentUserId();
        UserResponse response = userService.updateProfile(userId, request);
        return ApiResponse.success(response, "Profile updated successfully.");
    }

    @PostMapping(value = "/edit-profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Update user avatar", description = "Uploads and updates the authenticated user's avatar. Only JPEG/PNG files under 2MB are accepted.")
    public ApiResponse<UserResponse> updateAvatar(
            @RequestPart(value = "avatar") MultipartFile avatar) {
        UUID userId = getCurrentUserId();
        UserResponse response = userService.updateAvatar(userId, avatar);
        return ApiResponse.success(response, "Avatar updated successfully.");
    }


    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Change password", description = "Allows authenticated user to change their password by validating the current password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = getCurrentUserId();
        authService.changePassword(userId, request);
        return ApiResponse.success("Password changed successfully.");
    }

    @PostMapping("/preferred-tags")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Save preferred tags", description = "Saves user's preferred tags (1-3 public tags) from the onboarding survey.")
    public ApiResponse<Void> savePreferredTags(@Valid @RequestBody SavePreferredTagsRequest request) {
        UUID userId = getCurrentUserId();
        userService.savePreferredTags(userId, request.getTagIds());
        return ApiResponse.success("Preferred tags saved successfully.");
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getId();
    }
}
