package vn.ai_study_hub_api.service;

import java.util.UUID;

public interface UserSanctionService {
    void banUser(UUID userId);
    void warnUser(UUID userId, String reason);
    void trackUserToken(UUID userId, String token);
}
