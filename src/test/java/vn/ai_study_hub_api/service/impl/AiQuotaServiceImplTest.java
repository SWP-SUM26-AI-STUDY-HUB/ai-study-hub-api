package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.StoragePlanEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.AiQuotaService;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiQuotaServiceImplTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StoragePlanRepository storagePlanRepository;

    @InjectMocks
    private AiQuotaServiceImpl aiQuotaService;

    private UUID userId;
    private String key;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        key = "user:ai_limit:" + userId + ":" + LocalDate.now();
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    private UserEntity userWithPlan(Integer planId) {
        return UserEntity.builder().id(userId).planId(planId).build();
    }

    private StoragePlanEntity planWithLimit(int limit) {
        return StoragePlanEntity.builder().id(1).name("Free").maxAiRequestsPerDay(limit).build();
    }

    @Test
    void checkAndIncrement_firstRequestOfDay_increments_setsTtl_returnsRemaining() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithPlan(1)));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(planWithLimit(15)));
        when(valueOps.get(key)).thenReturn(null);
        when(valueOps.increment(key)).thenReturn(1L);

        AiQuotaService.QuotaInfo info = aiQuotaService.checkAndIncrement(userId);

        assertEquals(1, info.currentCount());
        assertEquals(15, info.dailyLimit());
        assertEquals(14, info.remaining());
        verify(valueOps, times(1)).increment(key);
        verify(redis, times(1)).expire(key, Duration.ofSeconds(86400));
    }

    @Test
    void checkAndIncrement_atLimit_blocksWithoutIncrementing() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithPlan(1)));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(planWithLimit(15)));
        when(valueOps.get(key)).thenReturn("15");

        AppException ex = assertThrows(AppException.class, () -> aiQuotaService.checkAndIncrement(userId));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals("Daily AI request limit reached. Please upgrade to Premium for more request chat",
                ex.getMessage());
        verify(valueOps, never()).increment(anyString());
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void checkAndIncrement_underLimit_incrementsAndDoesNotResetTtl() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithPlan(1)));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(planWithLimit(15)));
        when(valueOps.get(key)).thenReturn("5");
        when(valueOps.increment(key)).thenReturn(6L);

        AiQuotaService.QuotaInfo info = aiQuotaService.checkAndIncrement(userId);

        assertEquals(6, info.currentCount());
        assertEquals(9, info.remaining());
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void getUsage_returnsCurrentSnapshotWithoutIncrementing() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithPlan(1)));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(planWithLimit(15)));
        when(valueOps.get(key)).thenReturn("3");

        AiQuotaService.QuotaInfo info = aiQuotaService.getUsage(userId);

        assertEquals(3, info.currentCount());
        assertEquals(15, info.dailyLimit());
        assertEquals(12, info.remaining());
        verify(valueOps, never()).increment(anyString());
    }

    @Test
    void checkAndIncrement_nullPlan_defaultsToFreePlan() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithPlan(null)));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(planWithLimit(15)));
        when(valueOps.get(key)).thenReturn(null);
        when(valueOps.increment(key)).thenReturn(1L);

        AiQuotaService.QuotaInfo info = aiQuotaService.checkAndIncrement(userId);

        assertEquals(1, info.currentCount());
        verify(storagePlanRepository, times(1)).findById(1);
    }
}
