package vn.ai_study_hub_api.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.ai_study_hub_api.controller.response.UserResponse;
import vn.ai_study_hub_api.controller.response.UserStorageResponse;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserRole;
import vn.ai_study_hub_api.model.UserStatus;

import java.util.List;

public interface UserService {


    List<UserResponse> getAllUsers();
    Page<UserResponse> getUsers(String search, UserRole role, UserStatus status, Pageable pageable);
    UserEntity createOrUpdateUserFromOAuth2(String email, String fullName, String avatarUrl, String googleId );
    UserResponse updateProfile(java.util.UUID userId, vn.ai_study_hub_api.controller.request.UpdateProfileRequest request);
    UserResponse updateAvatar(java.util.UUID userId, org.springframework.web.multipart.MultipartFile avatar);
    UserResponse getMyProfile(java.util.UUID userId);
    UserStorageResponse getUserStorage(java.util.UUID userId);
}
