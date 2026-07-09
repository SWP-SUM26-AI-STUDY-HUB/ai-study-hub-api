package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides when a repeatedly-failing stream message has exhausted its retries and should be moved to
 * the dead-letter stream. The delivery count is read straight from the consumer group's PEL
 * ({@code XPENDING}), so no per-message attempt state is tracked on our side.
 */
@Component
@RequiredArgsConstructor
public class ModerationDlqHandler {

    /** Upper bound on pending messages scanned per delivery-count lookup (moderation volume is low). */
    static final long PENDING_SCAN_LIMIT = 256L;

    private final StringRedisTemplate redis;

    /** @return {@code true} when the message has been delivered at least {@code maxAttempts} times. */
    public boolean shouldDeadLetter(String streamKey, String group, RecordId recordId, int maxAttempts) {
        PendingMessages pending = redis.opsForStream()
                .pending(streamKey, group, Range.unbounded(), PENDING_SCAN_LIMIT);
        if (pending == null) {
            return false;
        }
        for (PendingMessage pm : pending) {
            if (recordId.equals(pm.getId())) {
                return pm.getTotalDeliveryCount() >= maxAttempts;
            }
        }
        return false;
    }

    /** Re-posts the message payload to the DLQ, tagged with the failure reason and original stream id. */
    public void moveToDlq(String dlqKey, MapRecord<String, String, String> record, String reason) {
        Map<String, String> body = new HashMap<>(record.getValue());
        body.put("original_id", record.getId().getValue());
        body.put("error", reason == null ? "unknown" : reason);
        redis.opsForStream().add(dlqKey, body);
    }
}
