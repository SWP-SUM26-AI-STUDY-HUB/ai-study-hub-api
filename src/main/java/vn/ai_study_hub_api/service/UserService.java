package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.response.UserResponse;
import vn.ai_study_hub_api.model.UserEntity;

import java.util.List;

public interface UserService {


    List<UserResponse> getAllUsers();
    UserEntity createOrUpdateUserFromOAuth2(String email, String fullName, String avatarUrl, String googleId );
}
