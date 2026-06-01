package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.request.RegisterRequest;
import vn.ai_study_hub_api.service.AuthService;
import vn.ai_study_hub_api.controller.response.LoginResponse;
import vn.ai_study_hub_api.controller.request.LoginRequest;
import vn.ai_study_hub_api.controller.request.RefreshTokenRequest;

/**
 * Controller exposing authentication endpoints: Login, Refresh Token, Logout, Register, Verify, and Resend OTP.
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

    /**
     * Register a new user account.
     * @param request Registration details
     * @return ApiResponse with success message and data: null
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Register a new user account", description = "Creates a pending user account and logs/sends an OTP code")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success("Registration successful. Please check your console/log for the OTP code.");
    }

    /**
     * Verify user account using OTP.
     * @param email User email
     * @param otp Active OTP code
     * @return ApiResponse with success message and data: null
     */
    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Verify account via OTP", description = "Activates the pending user account if the provided OTP matches and is valid")
    public ApiResponse<Void> verify(
            @Parameter(description = "User email to verify") @RequestParam String email,
            @Parameter(description = "6-digit OTP code") @RequestParam String otp) {
        authService.verifyAccount(email, otp);
        return ApiResponse.success("Account activated successfully.");
    }

    /**
     * Generate and resend a new OTP code to user email.
     * @param email User email
     * @return ApiResponse with success message and data: null
     */
    @PostMapping("/resend-otp")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Resend validation OTP", description = "Generates a new OTP code and resends it to the user's email if the rate limit allows")
    public ApiResponse<Void> resendOtp(
            @Parameter(description = "User email to resend OTP to") @RequestParam("email") String email) {
        authService.resendOtp(email);
        return ApiResponse.success("A new OTP code has been resent successfully.");
    }
}