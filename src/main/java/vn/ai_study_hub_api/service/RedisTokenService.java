package vn.ai_study_hub_api.service;


public interface RedisTokenService {


    void saveRefreshToken(String userId, String refreshToken, long ttlSeconds);

    String getRefreshToken(String userId);


    void deleteRefreshToken(String userId);


    void blacklistAccessToken(String accessToken, long ttlSeconds);


    boolean isAccessTokenBlacklisted(String accessToken);
    void saveOtp(String email, String otp, long ttlSeconds);
    String getOtp(String email);
    void deleteOtp(String email);
}
