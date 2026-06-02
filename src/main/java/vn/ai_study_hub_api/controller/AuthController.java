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
import vn.ai_study_hub_api.controller.request.*;
import vn.ai_study_hub_api.service.AuthService;
import vn.ai_study_hub_api.controller.response.LoginResponse;


@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for user authentication management")
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }


    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Authenticate user credentials", description = "Validate user email and password, returning dual JWT tokens (Access and Refresh)")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.success(response, "Login successful.");
    }


    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Renew expired access token", description = "Generate a new access token and rotate the refresh token using a valid active refresh token")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refreshToken(request);
        return ApiResponse.success(response, "Token refreshed successfully.");
    }

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


    @PostMapping("/resend-otp")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Resend validation OTP", description = "Generates a new OTP code and resends it to the user's email if the rate limit allows")
    public ApiResponse<Void> resendOtp(
            @Parameter(description = "User email to resend OTP to") @RequestParam("email") String email) {
        authService.resendOtp(email);
        return ApiResponse.success("A new OTP code has been resent successfully.");
    }
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Request password reset", description = "Validates email and sends a reset token via email")
    public ApiResponse<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);

        // Tham số 1 (Data): Chuỗi thông báo thành công cho mục "data"
        // Tham số 2 (Message): Chuỗi hiển thị ở mục "message"
        return ApiResponse.success("Reset password email has been sent successfully!", "Request processed successfully.");
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Reset password using token", description = "Validates the reset token and updates the user password")
    public ApiResponse<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);

        // Tham số 1 (Data): Chuỗi thông báo thành công cho mục "data"
        // Tham số 2 (Message): Chuỗi hiển thị ở mục "message"
        return ApiResponse.success("Password has been reset successfully!", "Request processed successfully.");
    }
}