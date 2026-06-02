package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.request.*;
import vn.ai_study_hub_api.controller.response.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse refreshToken(RefreshTokenRequest request);

    void logout(String authHeader);
    void register(RegisterRequest request);
    void verifyAccount(String email, String otp);
    void resendOtp(String email);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);

}
