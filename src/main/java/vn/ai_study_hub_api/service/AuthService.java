package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.request.LoginRequest;
import vn.ai_study_hub_api.controller.request.RefreshTokenRequest;
import vn.ai_study_hub_api.controller.request.RegisterRequest;
import vn.ai_study_hub_api.controller.response.LoginResponse;

/**
 * Service interface defining core authentication business operations.
 */
public interface AuthService {

    /**
     * Authenticate user credentials and return access and refresh tokens.
     * @param request Login credentials payload
     * @return LoginResponse containing profile and tokens
     */
    LoginResponse login(LoginRequest request);

    /**
     * Renew an access token using a valid active refresh token.
     * @param request Refresh token payload
     * @return LoginResponse containing new tokens
     */
    LoginResponse refreshToken(RefreshTokenRequest request);

    /**
     * Log out the current user, clear session, and blacklist current access token in Redis.
     * @param authHeader HTTP Authorization header value
     */
    void logout(String authHeader);
    void register(RegisterRequest request);
    void verifyAccount(String email, String otp);
    void resendOtp(String email);
}
