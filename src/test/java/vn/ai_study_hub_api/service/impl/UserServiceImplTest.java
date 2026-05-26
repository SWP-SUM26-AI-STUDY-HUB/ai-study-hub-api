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
import vn.ai_study_hub_api.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserServiceImpl utilizing JUnit 5 and Mockito.
 * Tests retrieving all users when database has data and when it is empty.
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
        // Initialize mock user data before each test case to guarantee complete test isolation
        mockUser1 = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .fullName("Alice Green")
                .avatarUrl("http://example.com/alice.png")
                .role("user")
                .status("active")
                .build();

        mockUser2 = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("bob@example.com")
                .fullName("Bob Miller")
                .avatarUrl("http://example.com/bob.png")
                .role("admin")
                .status("active")
                .build();

        mockUserList = new ArrayList<>();
        mockUserList.add(mockUser1);
        mockUserList.add(mockUser2);
    }

    @AfterEach
    void tearDown() {
        // Clear mock references after each execution to preserve isolation
        mockUser1 = null;
        mockUser2 = null;
        mockUserList = null;
    }

    /**
     * Case 1: Test getting user list successfully when there is data in database.
     * Expected: Returns a non-empty list of UserResponse objects with correct mapped profiles.
     */
    @Test
    void getAllUsers_Success_WithData() {
        // Arrange (Setup mock repository returning our stubbed list)
        when(userRepository.findAll()).thenReturn(mockUserList);

        // Act (Execute the target service logic)
        List<UserResponse> responseList = userService.getAllUsers();

        // Assert (Validate structural correctness and properties)
        assertNotNull(responseList, "The response list should not be null");
        assertFalse(responseList.isEmpty(), "The response list should not be empty");
        assertEquals(2, responseList.size(), "The response list size should be 2");

        // Verify mapping details of the first user
        UserResponse response1 = responseList.get(0);
        assertEquals(mockUser1.getId(), response1.getId());
        assertEquals(mockUser1.getEmail(), response1.getEmail());
        assertEquals(mockUser1.getFullName(), response1.getFullName());
        assertEquals(mockUser1.getAvatarUrl(), response1.getAvatarUrl());
        assertEquals(mockUser1.getRole(), response1.getRole());
        assertEquals(mockUser1.getStatus(), response1.getStatus());

        // Verify mapping details of the second user
        UserResponse response2 = responseList.get(1);
        assertEquals(mockUser2.getId(), response2.getId());
        assertEquals(mockUser2.getEmail(), response2.getEmail());
        assertEquals(mockUser2.getFullName(), response2.getFullName());
        assertEquals(mockUser2.getAvatarUrl(), response2.getAvatarUrl());
        assertEquals(mockUser2.getRole(), response2.getRole());
        assertEquals(mockUser2.getStatus(), response2.getStatus());

        // Verify mock interactions
        verify(userRepository, times(1)).findAll();
    }

    /**
     * Case 2: Test getting user list when database has no user records.
     * Expected: Returns an empty list (non-null but size = 0).
     */
    @Test
    void getAllUsers_Success_EmptyList() {
        // Arrange (Repository returns empty stub)
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<UserResponse> responseList = userService.getAllUsers();

        // Assert
        assertNotNull(responseList, "The response list should not be null");
        assertTrue(responseList.isEmpty(), "The response list should be empty");
        assertEquals(0, responseList.size(), "The response list size should be 0");

        // Verify mock interactions
        verify(userRepository, times(1)).findAll();
    }
}
