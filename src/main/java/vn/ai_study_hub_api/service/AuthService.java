package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.request.LoginRequest;
import vn.ai_study_hub_api.controller.request.RefreshTokenRequest;
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
     * Generate Google Auth URL based on login type.
     * @param loginType The social provider (e.g., "google")
     * @return The redirection URL string for client
     */
    String generateAuthUrl(String loginType);

    /**
     * Process Google OAuth2 callback code and issue system tokens.
     * @param code The authorization code received from Google
     * @return LoginResponse containing system profile and dual JWT tokens
     */
    LoginResponse processGoogleLogin(String code);

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
}
