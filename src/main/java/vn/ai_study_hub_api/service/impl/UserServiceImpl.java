package vn.ai_study_hub_api.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.controller.response.UserResponse;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.UserService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service implementation for managing users and OAuth2 account provisioning.
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<UserResponse> getAllUsers() {
        List<UserEntity> users = userRepository.findAll();
        return users.stream()
                .map(user -> UserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .role(user.getRole())
                        .status(user.getStatus())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserEntity createOrUpdateUserFromOAuth2(String email, String fullName, String avatarUrl) {
        return userRepository.findByEmail(email)
                .map(existingUser -> {
                    if (fullName != null) existingUser.setFullName(fullName);
                    if (avatarUrl != null) existingUser.setAvatarUrl(avatarUrl);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    UserEntity newUser = new UserEntity();
                    newUser.setEmail(email);
                    newUser.setFullName(fullName != null ? fullName : "Google User");
                    newUser.setAvatarUrl(avatarUrl);
                    newUser.setRole("USER");
                    newUser.setStatus("active");
                    newUser.setPasswordHash(null);
                    return userRepository.save(newUser);
                });
    }
}