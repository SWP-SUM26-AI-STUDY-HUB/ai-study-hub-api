package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.ai_study_hub_api.service.AutoModerationService;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Consumes {@code stream:moderation}: runs {@link AutoModerationService#process} for each document,
 * ACKs on success (or on an idempotent skip), and leaves the message unacked on failure so it is
 * retried — then moved to the DLQ by {@link ModerationDlqHandler} once
 * {@code app.moderation.max-attempts} is reached.
 *
 * <p>Because {@code XREADGROUP ... >} only returns never-delivered messages, a periodic
 * {@link #reclaimStale} re-claims messages still sitting in the PEL (idle &gt; {@code min-idle-ms})
 * and re-runs them through the same {@link #onMessage} path. That is what makes failed / restarted
 * jobs actually retry instead of lingering in the PEL forever.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModerationStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final AutoModerationService moderationService;
    private final StringRedisTemplate redis;
    private final ModerationDlqHandler dlqHandler;

    @Value("${app.moderation.stream-key:stream:moderation}")
    private String streamKey;
    @Value("${app.moderation.group:moderation-cg}")
    private String group;
    @Value("${app.moderation.consumer-name:api-1}")
    private String consumer;
    @Value("${app.moderation.max-attempts:5}")
    private int maxAttempts;
    @Value("${app.moderation.dlq-key:stream:moderation:dlq}")
    private String dlqKey;
    @Value("${app.moderation.reclaim.enabled:true}")
    private boolean reclaimEnabled;
    @Value("${app.moderation.reclaim.min-idle-ms:300000}")
    private long reclaimMinIdleMs;

    /** Exposed so {@link ModerationStreamConfig} can build the group consumer from the same config. */
    public String getConsumerName() {
        return consumer;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        UUID documentId;
        try {
            documentId = UUID.fromString(record.getValue().get("document_id"));
        } catch (Exception e) {
            // Poison message (bad/missing payload) — never recoverable; straight to DLQ + ACK.
            log.error("Poison moderation message {}: moving to DLQ", record.getId(), e);
            dlqHandler.moveToDlq(dlqKey, record, "bad payload: " + e.getMessage());
            redis.opsForStream().acknowledge(streamKey, group, record.getId());
            return;
        }

        try {
            moderationService.process(documentId);
            redis.opsForStream().acknowledge(streamKey, group, record.getId());
        } catch (Exception e) {
            log.error("Moderation failed for document {} (id={}); leaving unacked for retry",
                    documentId, record.getId(), e);
            if (dlqHandler.shouldDeadLetter(streamKey, group, record.getId(), maxAttempts)) {
                log.warn("Moderation for document {} reached {} attempts; moving to DLQ {}",
                        documentId, maxAttempts, dlqKey);
                dlqHandler.moveToDlq(dlqKey, record, e.getMessage());
                redis.opsForStream().acknowledge(streamKey, group, record.getId());
            }
            // else: stay unacked → reclaimStale() redelivers after min-idle.
        }
    }

    /**
     * Reclaims messages stuck in the PEL (delivered but never ACKed) and reprocesses them. Handles
     * both transient failures awaiting retry and consumers that crashed mid-processing. {@code XCLAIM}
     * only returns messages actually idle {@code >= min-idle-ms}, so in-flight work is not stolen.
     */
    @Scheduled(fixedDelayString = "${app.moderation.reclaim.fixed-delay-ms:60000}")
    public void reclaimStale() {
        if (!reclaimEnabled) {
            return;
        }
        PendingMessages pending = redis.opsForStream()
                .pending(streamKey, group, Range.unbounded(), ModerationDlqHandler.PENDING_SCAN_LIMIT);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        Duration minIdle = Duration.ofMillis(reclaimMinIdleMs);
        RecordId[] staleIds = pending.stream().map(PendingMessage::getId).toArray(RecordId[]::new);
        StreamOperations<String, String, String> streamOps = redis.opsForStream();
        List<MapRecord<String, String, String>> claimed = streamOps
                .claim(streamKey, group, consumer, minIdle, staleIds);
        for (MapRecord<String, String, String> record : claimed) {
            onMessage(record);
        }
    }
}
