package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.ai_study_hub_api.controller.request.LoginRequest;
import vn.ai_study_hub_api.controller.response.LoginResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserRole;   // SỬA: Import Enum UserRole
import vn.ai_study_hub_api.model.UserStatus; // SỬA: Import Enum UserStatus
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.security.JwtTokenProvider;
import vn.ai_study_hub_api.service.RedisTokenService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private RedisTokenService redisTokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    private UserEntity mockUser;
    private LoginRequest validLoginRequest;

    @BeforeEach
    void setUp() {
        // SỬA: Chuyển đổi giá trị "user" và "active" từ String sang Enum chuẩn xác
        mockUser = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("testuser@example.com")
                .passwordHash("encoded_password_hash")
                .fullName("Test User")
                .role(UserRole.user)       // Đổi thành Enum
                .status(UserStatus.active) // Đổi thành Enum
                .build();

        validLoginRequest = new LoginRequest("testuser@example.com", "Password123!");
    }

    @AfterEach
    void tearDown() {
        mockUser = null;
        validLoginRequest = null;
    }

    @Test
    void login_Success() {
        when(userRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(validLoginRequest.getPassword(), mockUser.getPasswordHash())).thenReturn(true);
        when(tokenProvider.generateAccessToken(any(CustomUserDetails.class))).thenReturn("mocked_access_token");
        when(tokenProvider.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("mocked_refresh_token");
        when(tokenProvider.getJwtRefreshExpirationMs()).thenReturn(604800000L); // 7 days in ms

        LoginResponse response = authService.login(validLoginRequest);

        assertNotNull(response, "LoginResponse should not be null on successful login");
        assertEquals("mocked_access_token", response.getAccessToken());
        assertEquals("mocked_refresh_token", response.getRefreshToken());
        assertEquals(mockUser.getId(), response.getId());
        assertEquals(mockUser.getEmail(), response.getEmail());
        assertEquals(mockUser.getFullName(), response.getFullName());

        // SỬA: LoginResponse trả về trường role dạng String nên cần sử dụng .name() từ Enum mockUser
        assertEquals(mockUser.getRole().name(), response.getRole());

        verify(redisTokenService, times(1)).saveRefreshToken(
                eq(mockUser.getId().toString()),
                eq("mocked_refresh_token"),
                eq(604800L) // 7 days in seconds
        );
    }

    @Test
    void login_Failure_IncorrectPassword() {
        LoginRequest wrongPasswordRequest = new LoginRequest("testuser@example.com", "WrongPassword!");
        when(userRepository.findByEmail(wrongPasswordRequest.getEmail())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(wrongPasswordRequest.getPassword(), mockUser.getPasswordHash())).thenReturn(false);

        AppException exception = assertThrows(AppException.class, () -> authService.login(wrongPasswordRequest));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("Invalid email or password.", exception.getMessage());

        verify(tokenProvider, never()).generateAccessToken(any(CustomUserDetails.class));
        verify(redisTokenService, never()).saveRefreshToken(anyString(), anyString(), anyLong());
    }

    @Test
    void login_Failure_UsernameNotFound() {
        LoginRequest nonExistentUserRequest = new LoginRequest("unknown@example.com", "Password123!");
        when(userRepository.findByEmail(nonExistentUserRequest.getEmail())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> authService.login(nonExistentUserRequest));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("Invalid email or password.", exception.getMessage());

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(tokenProvider, never()).generateAccessToken(any(CustomUserDetails.class));
    }
}