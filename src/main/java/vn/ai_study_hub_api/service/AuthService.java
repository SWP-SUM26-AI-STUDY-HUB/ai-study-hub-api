package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.request.LoginRequest;
import vn.ai_study_hub_api.controller.request.RefreshTokenRequest;
import vn.ai_study_hub_api.controller.response.LoginResponse;


public interface AuthService {


    LoginResponse login(LoginRequest request);



    String generateAuthUrl(String loginType);


    LoginResponse processGoogleLogin(String code);


    LoginResponse refreshToken(RefreshTokenRequest request);


    void logout(String authHeader);
}
