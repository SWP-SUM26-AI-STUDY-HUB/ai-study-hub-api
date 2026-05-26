package vn.ai_study_hub_api.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.ai_study_hub_api.controller.response.UserResponse;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.UserService;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service implementation for managing users.
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
}
