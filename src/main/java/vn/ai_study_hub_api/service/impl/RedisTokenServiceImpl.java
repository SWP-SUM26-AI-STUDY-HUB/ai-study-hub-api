package vn.ai_study_hub_api.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.ai_study_hub_api.service.RedisTokenService;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of RedisTokenService using Spring's StringRedisTemplate.
 */
@Service
public class RedisTokenServiceImpl implements RedisTokenService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String BLACKLIST_TOKEN_PREFIX = "blacklist_token:";
    private static final String OTP_PREFIX = "otp:"; // Prefix cho OTP

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public RedisTokenServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveRefreshToken(String userId, String refreshToken, long ttlSeconds) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        redisTemplate.opsForValue().set(key, refreshToken, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public String getRefreshToken(String userId) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void deleteRefreshToken(String userId) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        redisTemplate.delete(key);
    }

    @Override
    public void blacklistAccessToken(String accessToken, long ttlSeconds) {
        if (ttlSeconds > 0) {
            String key = BLACKLIST_TOKEN_PREFIX + accessToken;
            redisTemplate.opsForValue().set(key, "revoked", ttlSeconds, TimeUnit.SECONDS);
        }
    }

    @Override
    public boolean isAccessTokenBlacklisted(String accessToken) {
        String key = BLACKLIST_TOKEN_PREFIX + accessToken;
        Boolean hasKey = redisTemplate.hasKey(key);
        return hasKey != null && hasKey;
    }

    @Override
    public void saveOtp(String email, String otp, long ttlSeconds) {
        String key = "otp:" + email;
        redisTemplate.opsForValue().set(key, otp, ttlSeconds, TimeUnit.SECONDS);
        System.out.println("DEBUG SAVING: Key=" + key + " | Value=" + otp);
    }

    @Override
    public String getOtp(String email) {
        String key = "otp:" + email;
        String value = redisTemplate.opsForValue().get(key);
        System.out.println("DEBUG GETTING: Key=" + key + " | FoundValue=" + value);
        return value;
    }

    @Override
    public void deleteOtp(String email) {
        String key = "otp:" + email; // Key chuẩn: otp:email
        redisTemplate.delete(key);
    }
}