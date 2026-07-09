package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ModerationDlqHandlerTest {

    private static final String STREAM = "stream:moderation";
    private static final String GROUP = "moderation-cg";

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private StreamOperations<String, String, String> streamOps;

    @InjectMocks
    private ModerationDlqHandler handler;

    private PendingMessages pendingWith(RecordId id, long deliveryCount) {
        return new PendingMessages(GROUP,
                List.of(new PendingMessage(id, Consumer.from(GROUP, "c1"), Duration.ZERO, deliveryCount)));
    }

    @Test
    void shouldDeadLetter_trueWhenDeliveryCountAtMax() {
        RecordId id = RecordId.of("1-0");
        doReturn(streamOps).when(redis).opsForStream();
        when(streamOps.pending(eq(STREAM), eq(GROUP), any(Range.class), eq(ModerationDlqHandler.PENDING_SCAN_LIMIT)))
                .thenReturn(pendingWith(id, 5L));

        assertTrue(handler.shouldDeadLetter(STREAM, GROUP, id, 5));
    }

    @Test
    void shouldDeadLetter_falseBelowMax() {
        RecordId id = RecordId.of("1-1");
        doReturn(streamOps).when(redis).opsForStream();
        when(streamOps.pending(eq(STREAM), eq(GROUP), any(Range.class), eq(ModerationDlqHandler.PENDING_SCAN_LIMIT)))
                .thenReturn(pendingWith(id, 3L));

        assertFalse(handler.shouldDeadLetter(STREAM, GROUP, id, 5));
    }

    @Test
    void shouldDeadLetter_falseWhenMessageNotInPending() {
        doReturn(streamOps).when(redis).opsForStream();
        when(streamOps.pending(eq(STREAM), eq(GROUP), any(Range.class), eq(ModerationDlqHandler.PENDING_SCAN_LIMIT)))
                .thenReturn(new PendingMessages(GROUP, List.of()));

        assertFalse(handler.shouldDeadLetter(STREAM, GROUP, RecordId.of("9-9"), 5));
    }

    @Test
    @SuppressWarnings("unchecked")
    void moveToDlq_repostsPayloadWithErrorAndOriginalId() {
        doReturn(streamOps).when(redis).opsForStream();
        MapRecord<String, String, String> record = mock(MapRecord.class);
        when(record.getValue()).thenReturn(new HashMap<>(Map.of("document_id", "doc-abc")));
        when(record.getId()).thenReturn(RecordId.of("9-9"));

        handler.moveToDlq("stream:moderation:dlq", record, "boom");

        ArgumentCaptor<Map<String, String>> body = ArgumentCaptor.forClass(Map.class);
        verify(streamOps).add(eq("stream:moderation:dlq"), body.capture());
        Map<String, String> captured = body.getValue();
        assertEquals("doc-abc", captured.get("document_id"));
        assertEquals("9-9", captured.get("original_id"));
        assertEquals("boom", captured.get("error"));
    }
}
