package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ModerationStreamProducerTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private StreamOperations<String, String, String> streamOps;

    @InjectMocks
    private ModerationStreamProducer producer;

    @Test
    void enqueue_appendsDocumentIdToStream() {
        ReflectionTestUtils.setField(producer, "streamKey", "stream:moderation");
        doReturn(streamOps).when(redis).opsForStream();
        UUID id = UUID.randomUUID();

        producer.enqueue(id);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> body = ArgumentCaptor.forClass(Map.class);
        verify(streamOps).add(eq("stream:moderation"), body.capture());
        assertEquals(id.toString(), body.getValue().get("document_id"));
    }
}
