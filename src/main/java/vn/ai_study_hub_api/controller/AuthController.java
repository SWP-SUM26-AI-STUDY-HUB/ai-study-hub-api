package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.request.RegisterRequest;
import vn.ai_study_hub_api.service.AuthService;
import vn.ai_study_hub_api.controller.response.LoginResponse;
import vn.ai_study_hub_api.controller.request.LoginRequest;
import vn.ai_study_hub_api.controller.request.RefreshTokenRequest;

/**
 * Controller exposing authentication endpoints: Login, Refresh Token, and Logout.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for user authentication management")
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticate user credentials and return Access/Refresh tokens.
     * @param request Login credentials
     * @return ApiResponse containing the LoginResponse profile and tokens
     */
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Authenticate user credentials", description = "Validate user email and password, returning dual JWT tokens (Access and Refresh)")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.success(response, "Login successful.");
    }

    /**
     * Renew an access token using a valid active refresh token.
     * @param request Refresh token payload
     * @return ApiResponse containing the rotated tokens and profile
     */
    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Renew expired access token", description = "Generate a new access token and rotate the refresh token using a valid active refresh token")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refreshToken(request);
        return ApiResponse.success(response, "Token refreshed successfully.");
    }

    /**
     * Log out the current user, clear session, and blacklist current access token in Redis.
     * @param authHeader Authorization header containing Bearer access token
     * @return ApiResponse with success message
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Revoke session and log out", description = "Blacklists the current access token and deletes the matching refresh token in Redis")
    public ApiResponse<Void> logout(
            @Parameter(description = "HTTP Authorization header containing the Bearer access token")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(authHeader);
        return ApiResponse.success("Logout successful.");
    }
    @PostMapping("/register")
    public ApiResponse<String> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success("Đăng ký thành công, check OTP trong console/log!");
    }

    @PostMapping("/verify")
    public ApiResponse<String> verify(@RequestParam String email, @RequestParam String otp) {
        authService.verifyAccount(email, otp);
        return ApiResponse.success("Kích hoạt thành công!");
    }
    @PostMapping("/resend-otp")
    public ResponseEntity<Void> resendOtp(@RequestParam String email) {
        authService.resendOtp(email);
        return ResponseEntity.ok().build();
    }
}
