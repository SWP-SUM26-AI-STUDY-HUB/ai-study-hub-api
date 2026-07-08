package vn.ai_study_hub_api.service;

import java.util.UUID;

public interface AutoModerationService {
    /**
     * Runs moderation synchronously for a document: fetch its chunks, call the OpenAI Moderation API,
     * and triage into auto-approve / auto-reject / leave-PENDING. Invoked by the
     * {@code stream:moderation} consumer ({@link ModerationStreamListener}); producers append a
     * {@code document_id} to the stream instead of calling this directly.
     *
     * <p>Idempotent: a no-op when the document is no longer PENDING, so at-least-once redelivery
     * is safe. Transient failures (e.g. OpenAI HTTP error) propagate so the consumer does not ACK
     * and the message is retried (then DLQ'd after {@code app.moderation.max-attempts}).
     *
     * @param documentId the UUID of the document to moderate
     */
    void process(UUID documentId);
}
