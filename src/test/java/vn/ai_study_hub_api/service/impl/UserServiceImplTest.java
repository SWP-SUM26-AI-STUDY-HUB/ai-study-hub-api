package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ai_study_hub_api.controller.response.UserResponse;
import vn.ai_study_hub_api.controller.response.UserStorageResponse;
import vn.ai_study_hub_api.model.StoragePlanEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserRole;   // SỬA: Import Enum Role nếu có
import vn.ai_study_hub_api.model.UserStatus; // SỬA: Import Enum Status nếu có
import org.springframework.http.HttpStatus;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.UploadProvider;
import vn.ai_study_hub_api.controller.request.UpdateProfileRequest;
import java.io.File;
import java.time.LocalDateTime;
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

    @Mock
    private StoragePlanRepository storagePlanRepository;

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
    void updateProfile_Success_WithFullNameAndBio() {
        // Arrange
        UUID userId = mockUser1.getId();
        String newFullName = "Updated Alice";
        String newBio = "My new bio";
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName(newFullName)
                .bio(newBio)
                .build();
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser1));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        UserResponse response = userService.updateProfile(userId, request);
        
        // Assert
        assertNotNull(response);
        assertEquals(newFullName, response.getFullName());
        assertEquals(newBio, response.getBio());
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    void updateProfile_Failure_UserNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("New Name")
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> 
                userService.updateProfile(userId, request));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void updateAvatar_Success_WithValidFile() throws java.io.IOException {
        // Arrange
        UUID userId = mockUser1.getId();
        org.springframework.web.multipart.MultipartFile mockAvatar = mock(org.springframework.web.multipart.MultipartFile.class);
        
        mockUser1.setAvatarUrl("avatars/old-avatar.png");
        
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javax.imageio.ImageIO.write(img, "png", baos);
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(baos.toByteArray());
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser1));
        when(mockAvatar.isEmpty()).thenReturn(false);
        when(mockAvatar.getSize()).thenReturn(1024L * 1024L); // 1MB
        when(mockAvatar.getContentType()).thenReturn("image/png");
        when(mockAvatar.getOriginalFilename()).thenReturn("avatar.png");
        when(mockAvatar.getInputStream()).thenReturn(bais);
        when(uploadProvider.getPublicUrl(anyString())).thenReturn("http://presigned-url-mock.com/avatar");
        
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        UserResponse response = userService.updateAvatar(userId, mockAvatar);
        
        // Assert
        assertNotNull(response);
        assertEquals("http://presigned-url-mock.com/avatar", response.getAvatarUrl());
        verify(uploadProvider, times(1)).upload(any(File.class), anyString(), eq("image/png"));
        verify(userRepository, times(1)).save(any(UserEntity.class));
        verify(uploadProvider, times(1)).delete("avatars/old-avatar.png");
    }

    @Test
    void updateAvatar_Failure_FileSizeExceedsLimit() {
        // Arrange
        UUID userId = mockUser1.getId();
        org.springframework.web.multipart.MultipartFile mockAvatar = mock(org.springframework.web.multipart.MultipartFile.class);
        
        when(mockAvatar.isEmpty()).thenReturn(false);
        when(mockAvatar.getSize()).thenReturn(3L * 1024L * 1024L); // 3MB (exceeds 2MB)
        
        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> 
                userService.updateAvatar(userId, mockAvatar));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("exceeds the 2MB limit"));
    }

    @Test
    void updateAvatar_Failure_UnsupportedFileFormat() throws java.io.IOException {
        // Arrange
        UUID userId = mockUser1.getId();
        org.springframework.web.multipart.MultipartFile mockAvatar = mock(org.springframework.web.multipart.MultipartFile.class);
        
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javax.imageio.ImageIO.write(img, "gif", baos);
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(baos.toByteArray());
        
        when(mockAvatar.isEmpty()).thenReturn(false);
        when(mockAvatar.getSize()).thenReturn(1024L);
        when(mockAvatar.getContentType()).thenReturn("image/gif"); // gif is unsupported
        when(mockAvatar.getOriginalFilename()).thenReturn("avatar.gif");
        when(mockAvatar.getInputStream()).thenReturn(bais);
        
        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> 
                userService.updateAvatar(userId, mockAvatar));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("Unsupported file format"));
    }

    @Test
    void updateAvatar_Failure_UserNotFound() throws java.io.IOException {
        // Arrange
        UUID userId = UUID.randomUUID();
        org.springframework.web.multipart.MultipartFile mockAvatar = mock(org.springframework.web.multipart.MultipartFile.class);
        
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javax.imageio.ImageIO.write(img, "png", baos);
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(baos.toByteArray());
        
        when(mockAvatar.isEmpty()).thenReturn(false);
        when(mockAvatar.getSize()).thenReturn(1024L);
        when(mockAvatar.getContentType()).thenReturn("image/png");
        when(mockAvatar.getOriginalFilename()).thenReturn("avatar.png");
        when(mockAvatar.getInputStream()).thenReturn(bais);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> 
                userService.updateAvatar(userId, mockAvatar));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void getUserStorage_Success() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.builder()
                .id(userId)
                .planId(2)
                .storageUsed(536870912L) // 0.5 GB in bytes
                .build();

        StoragePlanEntity plan = StoragePlanEntity.builder()
                .id(2)
                .name("Premium")
                .storageLimit(10L * 1024L * 1024L * 1024L) // 10 GB in bytes
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(storagePlanRepository.findById(2)).thenReturn(Optional.of(plan));

        // Act
        UserStorageResponse response = userService.getUserStorage(userId);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getPlanId());
        assertEquals("Premium", response.getPlanName());
        assertEquals(536870912L, response.getStorageUsed());
        assertEquals(10L * 1024 * 1024 * 1024, response.getStorageLimit());
    }

    @Test
    void getUserStorage_Success_WithDefaultPlanId() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.builder()
                .id(userId)
                .planId(null) // should default to 1
                .storageUsed(1073741824L) // 1 GB
                .build();

        StoragePlanEntity plan = StoragePlanEntity.builder()
                .id(1)
                .name("Free Plan")
                .storageLimit(2L * 1024L * 1024L * 1024L) // 2 GB in bytes
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(plan));

        // Act
        UserStorageResponse response = userService.getUserStorage(userId);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getPlanId());
        assertEquals("Free Plan", response.getPlanName());
        assertEquals(1073741824L, response.getStorageUsed());
        assertEquals(2L * 1024 * 1024 * 1024, response.getStorageLimit());
    }

    @Test
    void getUserStorage_Failure_UserNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () ->
                userService.getUserStorage(userId));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertTrue(exception.getMessage().contains("User profile not found"));
    }

    @Test
    void getUserStorage_Failure_PlanNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.builder()
                .id(userId)
                .planId(999)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(storagePlanRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () ->
                userService.getUserStorage(userId));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertTrue(exception.getMessage().contains("Storage plan not found"));
    }
    @Test
    void downgradeToFreePlan_expiredPremiumUser_downgradesAndFlagsOverLimit() {
        mockUser1.setPlanId(2);
        mockUser1.setPlanExpiresAt(LocalDateTime.now().minusDays(1));
        mockUser1.setStorageUsed(5L * 1024L * 1024L * 1024L); // 5GB > 2GB free limit

        StoragePlanEntity freePlan = StoragePlanEntity.builder()
                .id(1)
                .storageLimit(2L * 1024L * 1024L * 1024L)
                .build();
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(freePlan));

        boolean result = userService.downgradeToFreePlan(mockUser1);

        assertTrue(result);
        assertEquals(1, mockUser1.getPlanId());
        assertNull(mockUser1.getPlanExpiresAt());
        assertEquals(UserStatus.OVERLIMITSTORAGE, mockUser1.getStatus());
        verify(userRepository, times(1)).save(mockUser1);
    }

    @Test
    void downgradeToFreePlan_expiredPremiumUser_underLimit_staysActive() {
        mockUser1.setPlanId(2);
        mockUser1.setPlanExpiresAt(LocalDateTime.now().minusDays(1));
        mockUser1.setStorageUsed(500L * 1024L * 1024L); // 500MB < 2GB

        StoragePlanEntity freePlan = StoragePlanEntity.builder()
                .id(1)
                .storageLimit(2L * 1024L * 1024L * 1024L)
                .build();
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(freePlan));

        boolean result = userService.downgradeToFreePlan(mockUser1);

        assertTrue(result);
        assertEquals(1, mockUser1.getPlanId());
        assertNull(mockUser1.getPlanExpiresAt());
        assertEquals(UserStatus.ACTIVE, mockUser1.getStatus());
    }

    @Test
    void downgradeToFreePlan_alreadyFree_returnsFalseNoOp() {
        mockUser1.setPlanId(1);
        mockUser1.setPlanExpiresAt(null);

        boolean result = userService.downgradeToFreePlan(mockUser1);

        assertFalse(result);
        verify(storagePlanRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }
}