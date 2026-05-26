package vn.ai_study_hub_api.service;

/**
 * Interface defining Redis token caching operations for Refresh Tokens and blacklisted Access Tokens.
 */
public interface RedisTokenService {

    /**
     * Store the refresh token in Redis with a Time To Live (TTL).
     * @param userId User UUID as string
     * @param refreshToken Refresh token value
     * @param ttlSeconds TTL in seconds
     */
    void saveRefreshToken(String userId, String refreshToken, long ttlSeconds);

    /**
     * Retrieve the stored refresh token for a user.
     * @param userId User UUID as string
     * @return Stored refresh token or null if not exists
     */
    String getRefreshToken(String userId);

    /**
     * Delete the refresh token for a user (on logout or revocation).
     * @param userId User UUID as string
     */
    void deleteRefreshToken(String userId);

    /**
     * Blacklist an access token with TTL matching its remaining life.
     * @param accessToken Access token value
     * @param ttlSeconds TTL in seconds
     */
    void blacklistAccessToken(String accessToken, long ttlSeconds);

    /**
     * Check if an access token is in the blacklist.
     * @param accessToken Access token value
     * @return true if blacklisted, false otherwise
     */
    boolean isAccessTokenBlacklisted(String accessToken);
}
