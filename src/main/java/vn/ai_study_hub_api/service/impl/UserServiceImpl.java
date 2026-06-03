package vn.ai_study_hub_api.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.controller.response.UserResponse;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserRole;
import vn.ai_study_hub_api.model.UserStatus;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.UserService;

import java.util.List;
import java.util.stream.Collectors;


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

                        .role(user.getRole() != null ? user.getRole().name() : null)
                        .status(user.getStatus() != null ? user.getStatus().name() : null)
                        .build())
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
}