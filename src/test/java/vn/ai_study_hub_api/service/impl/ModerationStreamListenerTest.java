package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import vn.ai_study_hub_api.service.AutoModerationService;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ModerationStreamListenerTest {

    private static final String STREAM = "stream:moderation";
    private static final String GROUP = "moderation-cg";
    private static final String DLQ = "stream:moderation:dlq";

    @Mock
    private AutoModerationService moderationService;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private StreamOperations<String, String, String> streamOps;

    @Mock
    private ModerationDlqHandler dlqHandler;

    @InjectMocks
    private ModerationStreamListener listener;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "streamKey", STREAM);
        ReflectionTestUtils.setField(listener, "group", GROUP);
        ReflectionTestUtils.setField(listener, "maxAttempts", 5);
        ReflectionTestUtils.setField(listener, "dlqKey", DLQ);
    }

    @SuppressWarnings("unchecked")
    private MapRecord<String, String, String> record(String documentId, String id) {
        MapRecord<String, String, String> rec = mock(MapRecord.class);
        when(rec.getValue()).thenReturn(Map.of("document_id", documentId));
        when(rec.getId()).thenReturn(RecordId.of(id));
        return rec;
    }

    @Test
    void onMessage_success_acks() {
        UUID id = UUID.randomUUID();
        MapRecord<String, String, String> rec = record(id.toString(), "1-0");
        doReturn(streamOps).when(redis).opsForStream();

        listener.onMessage(rec);

        verify(moderationService).process(id);
        verify(streamOps).acknowledge(STREAM, GROUP, RecordId.of("1-0"));
        verify(dlqHandler, never()).moveToDlq(anyString(), any(), anyString());
    }

    @Test
    void onMessage_processThrowsBelowMax_leavesUnacked() {
        UUID id = UUID.randomUUID();
        MapRecord<String, String, String> rec = record(id.toString(), "2-0");
        doThrow(new RuntimeException("openai down")).when(moderationService).process(id);
        when(dlqHandler.shouldDeadLetter(eq(STREAM), eq(GROUP), any(), eq(5))).thenReturn(false);

        listener.onMessage(rec);

        verify(moderationService).process(id);
        // No ack, no DLQ — message stays in the PEL for reclaim to retry.
        verify(redis, never()).opsForStream();
        verify(dlqHandler, never()).moveToDlq(anyString(), any(), anyString());
    }

    @Test
    void onMessage_processThrowsAtMax_movesToDlqAndAcks() {
        UUID id = UUID.randomUUID();
        MapRecord<String, String, String> rec = record(id.toString(), "3-0");
        doThrow(new RuntimeException("openai down")).when(moderationService).process(id);
        when(dlqHandler.shouldDeadLetter(eq(STREAM), eq(GROUP), any(), eq(5))).thenReturn(true);
        doReturn(streamOps).when(redis).opsForStream();

        listener.onMessage(rec);

        verify(dlqHandler).moveToDlq(eq(DLQ), eq(rec), anyString());
        verify(streamOps).acknowledge(STREAM, GROUP, RecordId.of("3-0"));
    }

    @Test
    void onMessage_poisonPayload_movesToDlqAndAcks() {
        MapRecord<String, String, String> rec = mock(MapRecord.class);
        when(rec.getValue()).thenReturn(Map.of("document_id", "not-a-uuid"));
        when(rec.getId()).thenReturn(RecordId.of("4-0"));
        doReturn(streamOps).when(redis).opsForStream();

        listener.onMessage(rec);

        verify(moderationService, never()).process(any());
        verify(dlqHandler).moveToDlq(eq(DLQ), eq(rec), contains("bad payload"));
        verify(streamOps).acknowledge(STREAM, GROUP, RecordId.of("4-0"));
    }
}
