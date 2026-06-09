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
import org.springframework.http.HttpStatus;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.UploadProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserServiceImpl utilizing JUnit 5 and Mockito.
 */
@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UploadProvider uploadProvider;

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

    @Test
    void updateProfile_Success_WithFullNameAndAvatar() throws java.io.IOException {
        // Arrange
        UUID userId = mockUser1.getId();
        String newFullName = "Updated Alice";
        org.springframework.web.multipart.MultipartFile mockAvatar = mock(org.springframework.web.multipart.MultipartFile.class);
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser1));
        when(mockAvatar.isEmpty()).thenReturn(false);
        when(mockAvatar.getSize()).thenReturn(1024L * 1024L); // 1MB
        when(mockAvatar.getContentType()).thenReturn("image/png");
        when(mockAvatar.getOriginalFilename()).thenReturn("avatar.png");
        when(uploadProvider.generatePresignedUrl(anyString())).thenReturn("http://presigned-url-mock.com/avatar");
        
        // Mock userRepository.save
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        UserResponse response = userService.updateProfile(userId, newFullName, mockAvatar);
        
        // Assert
        assertNotNull(response);
        assertEquals(newFullName, response.getFullName());
        assertEquals("http://presigned-url-mock.com/avatar", response.getAvatarUrl());
        verify(uploadProvider, times(1)).upload(any(File.class), anyString(), eq("image/png"));
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    void updateProfile_Failure_FileSizeExceedsLimit() {
        // Arrange
        UUID userId = mockUser1.getId();
        org.springframework.web.multipart.MultipartFile mockAvatar = mock(org.springframework.web.multipart.MultipartFile.class);
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser1));
        when(mockAvatar.isEmpty()).thenReturn(false);
        when(mockAvatar.getSize()).thenReturn(3L * 1024L * 1024L); // 3MB (exceeds 2MB)
        
        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> 
                userService.updateProfile(userId, "New Name", mockAvatar));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("exceeds the 2MB limit"));
    }

    @Test
    void updateProfile_Failure_UnsupportedFileFormat() {
        // Arrange
        UUID userId = mockUser1.getId();
        org.springframework.web.multipart.MultipartFile mockAvatar = mock(org.springframework.web.multipart.MultipartFile.class);
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser1));
        when(mockAvatar.isEmpty()).thenReturn(false);
        when(mockAvatar.getSize()).thenReturn(1024L);
        when(mockAvatar.getContentType()).thenReturn("image/gif"); // gif is unsupported
        when(mockAvatar.getOriginalFilename()).thenReturn("avatar.gif");
        
        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> 
                userService.updateProfile(userId, "New Name", mockAvatar));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("Unsupported file format"));
    }

    @Test
    void updateProfile_Failure_UserNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> 
                userService.updateProfile(userId, "New Name", null));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }
}