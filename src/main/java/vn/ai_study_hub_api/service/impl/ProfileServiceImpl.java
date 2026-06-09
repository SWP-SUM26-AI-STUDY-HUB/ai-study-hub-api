package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.ai_study_hub_api.controller.response.UserResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.ProfileService;
import vn.ai_study_hub_api.service.UploadProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final UploadProvider uploadProvider;

    private static final long MAX_AVATAR_SIZE_BYTES = 2L * 1024 * 1024;
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png");
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png");

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMyProfile(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found."));
        return mapToResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UUID userId, String fullName, MultipartFile avatar) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found."));

        if (avatar != null && !avatar.isEmpty()) {
            String contentType = avatar.getContentType();
            if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Unsupported file format. Only JPEG and PNG are allowed");
            }

            if (avatar.getSize() > MAX_AVATAR_SIZE_BYTES) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Uploaded file size exceeds the 2MB limit. Please choose another file");
            }

            String ext = resolveExtension(avatar.getOriginalFilename(), contentType);
            String storagePath = "avatars/" + userId + "." + ext;

            File tempFile = null;
            try {
                tempFile = Files.createTempFile("avatar_" + userId, "." + ext).toFile();
                avatar.transferTo(tempFile);
                uploadProvider.upload(tempFile, storagePath, contentType);
                user.setAvatarUrl(storagePath);
                log.info("Avatar uploaded for user {} to path {}", userId, storagePath);
            } catch (IOException e) {
                throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to process avatar upload: " + e.getMessage());
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    boolean deleted = tempFile.delete();
                    log.debug("Cleaned up temp avatar file: {}, success: {}", tempFile.getAbsolutePath(), deleted);
                }
            }
        }

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }

        userRepository.save(user);
        log.info("Profile updated successfully for user {}", userId);
        return mapToResponse(user);
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            if (ALLOWED_EXTENSIONS.contains(ext)) {
                return ext;
            }
        }
        return contentType.contains("png") ? "png" : "jpg";
    }

    private UserResponse mapToResponse(UserEntity user) {
        String avatarUrl = null;
        if (user.getAvatarUrl() != null) {
            try {
                avatarUrl = uploadProvider.generatePresignedUrl(user.getAvatarUrl());
            } catch (Exception e) {
                log.warn("Could not generate presigned URL for avatar of user {}: {}", user.getId(), e.getMessage());
                avatarUrl = user.getAvatarUrl();
            }
        }

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(avatarUrl)
                .role(user.getRole() != null ? user.getRole().name() : null)
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .build();
    }
}
