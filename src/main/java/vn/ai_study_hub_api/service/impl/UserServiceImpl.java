package vn.ai_study_hub_api.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.ai_study_hub_api.controller.response.UserResponse;
import vn.ai_study_hub_api.controller.response.UserStorageResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.StoragePlanEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserRole;
import vn.ai_study_hub_api.model.UserStatus;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.UploadProvider;
import vn.ai_study_hub_api.service.UserService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UploadProvider uploadProvider;
    private final StoragePlanRepository storagePlanRepository;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, UploadProvider uploadProvider, StoragePlanRepository storagePlanRepository) {
        this.userRepository = userRepository;
        this.uploadProvider = uploadProvider;
        this.storagePlanRepository = storagePlanRepository;
    }

    @Override
    public List<UserResponse> getAllUsers() {
        List<UserEntity> users = userRepository.findAll();
        return users.stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserEntity createOrUpdateUserFromOAuth2(String email, String fullName, String avatarUrl, String googleId) {
        return userRepository.findByEmail(email)
                .map(existingUser -> {
                    // Nếu user đã tồn tại, kiểm tra xem có cần cập nhật googleId không
                    if (existingUser.getGoogleId() == null) {
                        existingUser.setGoogleId(googleId);
                    }
                    if (fullName != null) existingUser.setFullName(fullName);
                    if (avatarUrl != null) existingUser.setAvatarUrl(avatarUrl);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    UserEntity newUser = new UserEntity();
                    newUser.setEmail(email);
                    newUser.setFullName(fullName != null ? fullName : "Google User");
                    newUser.setAvatarUrl(avatarUrl);
                    newUser.setGoogleId(googleId);
                    newUser.setRole(UserRole.USER);
                    newUser.setStatus(UserStatus.ACTIVE);
                    return userRepository.save(newUser);
                });
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UUID userId, vn.ai_study_hub_api.controller.request.UpdateProfileRequest request) {
        UserEntity existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User profile not found."));

        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            existingUser.setFullName(request.getFullName().trim());
        }

        if (request.getBio() != null) {
            existingUser.setBio(request.getBio().trim().isEmpty() ? null : request.getBio().trim());
        }

        UserEntity savedUser = userRepository.save(existingUser);
        return mapToUserResponse(savedUser);
    }

    @Override
    @Transactional
    public UserResponse updateAvatar(UUID userId, MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Avatar file is required.");
        }

        // Kiểm tra kích thước file (<= 2MB = 2 * 1024 * 1024 bytes)
        if (avatar.getSize() > 2 * 1024 * 1024) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Uploaded file size exceeds the 2MB limit. Please choose another file");
        }

        String contentType = avatar.getContentType();
        String originalFilename = avatar.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.lastIndexOf('.') != -1) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }

        boolean isValidContentType = contentType != null && (contentType.equals("image/jpeg") || contentType.equals("image/png"));
        boolean isValidExtension = extension.equals("jpg") || extension.equals("jpeg") || extension.equals("png");

        if (!isValidContentType || (!extension.isEmpty() && !isValidExtension)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Unsupported file format. Only JPEG and PNG are allowed");
        }

        String finalExtension = extension;
        if (finalExtension.isEmpty() && contentType != null) {
            finalExtension = contentType.equals("image/png") ? "png" : "jpg";
        }

        UserEntity existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User profile not found."));

        String oldAvatarUrl = existingUser.getAvatarUrl();
        String s3Key = "avatars/" + userId.toString() + "-" + UUID.randomUUID().toString() + "." + finalExtension;

        File tempFile = null;
        try {
            tempFile = Files.createTempFile("avatar-" + userId, "." + extension).toFile();
            avatar.transferTo(tempFile);

            uploadProvider.upload(tempFile, s3Key, avatar.getContentType());
        } catch (IOException e) {
            log.error("Failed to upload avatar to S3 for user: {}", userId, e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload avatar to S3");
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        existingUser.setAvatarUrl(s3Key);
        UserEntity savedUser = userRepository.save(existingUser);

        // Xóa avatar cũ trên S3 nếu là file được upload nội bộ (không phải link external)
        if (oldAvatarUrl != null && !oldAvatarUrl.isEmpty() 
                && !oldAvatarUrl.startsWith("http://") && !oldAvatarUrl.startsWith("https://")) {
            try {
                uploadProvider.delete(oldAvatarUrl);
            } catch (Exception e) {
                log.warn("Failed to delete old avatar from S3: {}", oldAvatarUrl, e);
            }
        }

        return mapToUserResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMyProfile(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User profile not found."));
        return mapToUserResponse(user);
    }

    private UserResponse mapToUserResponse(UserEntity user) {
        String resolvedAvatarUrl = user.getAvatarUrl();
        if (resolvedAvatarUrl != null && !resolvedAvatarUrl.isEmpty() 
                && !resolvedAvatarUrl.startsWith("http://") && !resolvedAvatarUrl.startsWith("https://")) {
            try {
                resolvedAvatarUrl = uploadProvider.getPublicUrl(resolvedAvatarUrl);
            } catch (Exception e) {
                log.warn("Failed to generate public URL for avatar: {}", resolvedAvatarUrl, e);
            }
        }

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(resolvedAvatarUrl)
                .role(user.getRole() != null ? user.getRole().name() : null)
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .bio(user.getBio())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserStorageResponse getUserStorage(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User profile not found."));

        Integer planId = user.getPlanId() != null ? user.getPlanId() : 1;
        StoragePlanEntity plan = storagePlanRepository.findById(planId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Storage plan not found with ID: " + planId));

        long limitInBytes = plan.getStorageLimit() * 1024L * 1024L * 1024L;
        long usedInBytes = user.getStorageUsed() != null ? user.getStorageUsed() : 0L;

        return UserStorageResponse.builder()
                .planId(plan.getId())
                .planName(plan.getName())
                .storageUsed(usedInBytes)
                .storageLimit(limitInBytes)
                .build();
    }

}