package vn.ai_study_hub_api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.scheduling.annotation.EnableScheduling;
import vn.ai_study_hub_api.service.impl.ModerationStreamListener;

import java.time.Duration;

/**
 * Wires the {@code stream:moderation} consumer: creates the consumer group (idempotently, before the
 * container starts polling) and registers a {@link StreamMessageListenerContainer} that reads new
 * messages with manual ACK into {@link ModerationStreamListener}. Also enables {@code @Scheduled}
 * for the listener's PEL reclaim loop.
 *
 * <p>The container implements {@code SmartLifecycle}, so Spring starts/stops it with the application
 * context (graceful shutdown drains in-flight moderation rather than cutting it off).
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class ModerationStreamConfig {

    @Value("${app.moderation.stream-key:stream:moderation}")
    private String streamKey;

    @Value("${app.moderation.group:moderation-cg}")
    private String group;

    @Value("${app.moderation.poll-timeout-seconds:2}")
    private long pollTimeoutSeconds;

    @Value("${app.moderation.batch-size:1}")
    private int batchSize;

    @Bean
    public StreamMessageListenerContainer<String, org.springframework.data.redis.connection.stream.MapRecord<String, String, String>>
            moderationStreamContainer(RedisConnectionFactory connectionFactory,
                                      StringRedisTemplate redis,
                                      ModerationStreamListener listener) {
        // Group must exist before the container's first XREADGROUP (NOGROUP otherwise). Done in-bean so
        // it runs during context refresh, ahead of the container's SmartLifecycle.start().
        ensureConsumerGroup(redis);

        StreamMessageListenerContainerOptions<String, org.springframework.data.redis.connection.stream.MapRecord<String, String, String>>
                options = StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(pollTimeoutSeconds))
                .batchSize(batchSize)
                .build();

        StreamMessageListenerContainer<String, org.springframework.data.redis.connection.stream.MapRecord<String, String, String>>
                container = StreamMessageListenerContainer.create(connectionFactory, options);

        container.register(StreamMessageListenerContainer.StreamReadRequest
                        .builder(StreamOffset.create(streamKey, ReadOffset.lastConsumed()))   // ">" new messages only
                        .consumer(Consumer.from(group, listener.getConsumerName()))
                        .autoAcknowledge(false)                                              // ACK manually in the listener
                        .build(),
                listener);

        return container;
    }

    private void ensureConsumerGroup(StringRedisTemplate redis) {
        try {
            redis.opsForStream().createGroup(streamKey, ReadOffset.from("0"), group);
            log.info("Created moderation consumer group '{}' on stream '{}'", group, streamKey);
        } catch (DataAccessException e) {
            // BUSYGROUP = group already exists (normal on restart). Any other error would fail startup
            // (Redis is a hard dependency), which is the desired fail-fast behavior.
            log.info("Moderation group '{}' on '{}' already present: {}", group, streamKey,
                    e.getRootCause() != null ? e.getRootCause().getMessage() : e.getMessage());
        }
    }
}
