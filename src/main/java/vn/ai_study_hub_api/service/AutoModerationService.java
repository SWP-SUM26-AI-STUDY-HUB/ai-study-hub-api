package vn.ai_study_hub_api.service;

import java.util.UUID;

public interface AutoModerationService {
    /**
     * Moderates the document asynchronously by fetching its chunks,
     * calling OpenAI Moderation API, and deciding whether to auto-approve,
     * auto-reject, or leave it for manual admin review (pending).
     *
     * @param documentId the UUID of the document to moderate
     */
    void moderateDocumentAsync(UUID documentId);
}
