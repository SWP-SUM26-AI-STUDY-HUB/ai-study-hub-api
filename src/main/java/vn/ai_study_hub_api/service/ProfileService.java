package vn.ai_study_hub_api.service;

import org.springframework.web.multipart.MultipartFile;
import vn.ai_study_hub_api.controller.response.UserResponse;

import java.util.UUID;

public interface ProfileService {
    UserResponse getMyProfile(UUID userId);
    UserResponse updateProfile(UUID userId, String fullName, MultipartFile avatar);
}
