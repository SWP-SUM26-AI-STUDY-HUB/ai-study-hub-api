package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ai_study_hub_api.controller.response.UserResponse;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserRole;   // SỬA: Import Enum Role nếu có
import vn.ai_study_hub_api.model.UserStatus; // SỬA: Import Enum Status nếu có
import vn.ai_study_hub_api.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserServiceImpl utilizing JUnit 5 and Mockito.
 */
@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private UserEntity mockUser1;
    private UserEntity mockUser2;
    private List<UserEntity> mockUserList;

    @BeforeEach
    void setUp() {
        mockUser1 = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .fullName("Alice Green")
                .avatarUrl("http://example.com/alice.png")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        mockUser2 = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("bob@example.com")
                .fullName("Bob Miller")
                .avatarUrl("http://example.com/bob.png")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        mockUserList = new ArrayList<>();
        mockUserList.add(mockUser1);
        mockUserList.add(mockUser2);
    }

    @AfterEach
    void tearDown() {
        mockUser1 = null;
        mockUser2 = null;
        mockUserList = null;
    }

    @Test
    void getAllUsers_Success_WithData() {
        // Arrange
        when(userRepository.findAll()).thenReturn(mockUserList);

        // Act
        List<UserResponse> responseList = userService.getAllUsers();

        // Assert
        assertNotNull(responseList, "The response list should not be null");
        assertEquals(2, responseList.size(), "The response list size should be 2");

        // Verify mapping details user 1
        UserResponse response1 = responseList.get(0);
        assertEquals(mockUser1.getId(), response1.getId());
        assertEquals(mockUser1.getEmail(), response1.getEmail());
        assertEquals(mockUser1.getFullName(), response1.getFullName());
        assertEquals(mockUser1.getAvatarUrl(), response1.getAvatarUrl());

        // Cần lưu ý: Nếu UserResponse trả về String, hãy dùng .name() hoặc .toString() để so sánh với Enum
        assertEquals(mockUser1.getRole().name(), response1.getRole());
        assertEquals(mockUser1.getStatus().name(), response1.getStatus());

        // Verify mapping details user 2
        UserResponse response2 = responseList.get(1);
        assertEquals(mockUser2.getId(), response2.getId());
        assertEquals(mockUser2.getRole().name(), response2.getRole());
        assertEquals(mockUser2.getStatus().name(), response2.getStatus());

        verify(userRepository, times(1)).findAll();
    }

    @Test
    void getAllUsers_Success_EmptyList() {
        // Arrange
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<UserResponse> responseList = userService.getAllUsers();

        // Assert
        assertNotNull(responseList, "The response list should not be null");
        assertTrue(responseList.isEmpty(), "The response list should be empty");

        verify(userRepository, times(1)).findAll();
    }
}