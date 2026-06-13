package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.ProfileService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Validated
@Tag(name = "User Avatar", description = "Endpoints for user avatar management")
public class UserAvatarController {

    private final ProfileService profileService;

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Upload user avatar", description = "Uploads a new avatar image for the authenticated user. Only JPEG/PNG files under 2MB are accepted.")
    public ApiResponse<UserResponse> uploadAvatar(
            @Parameter(description = "Avatar image file (JPEG/PNG, max 2MB)")
            @RequestParam("avatar") MultipartFile avatar) {
        
        if (avatar == null || avatar.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Avatar file is required.");
        }
        String contentType = avatar.getContentType();
        String originalFilename = avatar.getOriginalFilename();
        if (contentType != null && contentType.equalsIgnoreCase("image/jpg")) {
            avatar = new MultipartFileWrapper(avatar, "image/jpeg");
        } else if (originalFilename != null) {
            String lowerName = originalFilename.toLowerCase();
            if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                if (contentType == null || !contentType.equalsIgnoreCase("image/jpeg")) {
                    avatar = new MultipartFileWrapper(avatar, "image/jpeg");
                }
            } else if (lowerName.endsWith(".png")) {
                if (contentType == null || !contentType.equalsIgnoreCase("image/png")) {
                    avatar = new MultipartFileWrapper(avatar, "image/png");
                }
            }
        }

        UUID userId = getCurrentUserId();
        UserResponse response = profileService.updateProfile(userId, null, avatar);
        return ApiResponse.success(response, "Avatar uploaded successfully.");
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getId();
    }

    private static class MultipartFileWrapper implements MultipartFile {
        private final MultipartFile delegate;
        private final String contentType;

        public MultipartFileWrapper(MultipartFile delegate, String contentType) {
            this.delegate = delegate;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getOriginalFilename() {
            return delegate.getOriginalFilename();
        }

        @Override
        public String getContentType() {
            return this.contentType;
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public long getSize() {
            return delegate.getSize();
        }

        @Override
        public byte[] getBytes() throws java.io.IOException {
            return delegate.getBytes();
        }

        @Override
        public java.io.InputStream getInputStream() throws java.io.IOException {
            return delegate.getInputStream();
        }

        @Override
        public void transferTo(java.io.File dest) throws java.io.IOException, IllegalStateException {
            delegate.transferTo(dest);
        }
    }
}
