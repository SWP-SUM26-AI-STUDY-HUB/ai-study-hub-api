package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Appends a moderation job to the {@code stream:moderation} Redis stream. Producers (the RAG
 * {@code EXTRACTED} callback and the PRIVATE→PUBLIC update path) call {@link #enqueue} instead of
 * the old {@code @Async} fire-and-forget; the durable stream + consumer group then own execution.
 */
@Component
@RequiredArgsConstructor
public class ModerationStreamProducer {

    private final StringRedisTemplate redis;

    @Value("${app.moderation.stream-key:stream:moderation}")
    private String streamKey;

    /**
     * Appends {@code {document_id}} and returns the generated stream id. Returns ~instantly (a single
     * Redis {@code XADD}); the consumer processes asynchronously and durably.
     */
    public RecordId enqueue(UUID documentId) {
        return redis.opsForStream().add(streamKey, Map.of("document_id", documentId.toString()));
    }
}
