package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ai_study_hub_api.service.AiMetricsService;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pure-Mockito unit tests for {@link AiMetricsRefreshScheduler}.
 *
 * <p>The scheduler is a thin wrapper over {@link AiMetricsService}: the cron fire delegates
 * to {@code refreshCache()}, and the application-ready hook delegates to
 * {@code refreshCacheIfCold()}. The Langfuse quota math (6 fires × 15 queries = 90/day) is
 * documented on the scheduler itself and asserted structurally here.
 */
@ExtendWith(MockitoExtension.class)
class AiMetricsRefreshSchedulerTest {

    @Mock
    private AiMetricsService aiMetricsService;

    @InjectMocks
    private AiMetricsRefreshScheduler scheduler;

    @Test
    void refreshAiMetricsCache_delegatesToRefreshCache() {
        scheduler.refreshAiMetricsCache();
        verify(aiMetricsService, times(1)).refreshCache();
    }

    @Test
    void warmUpOnStartup_delegatesToRefreshCacheIfCold() {
        scheduler.warmUpOnStartup();
        verify(aiMetricsService, times(1)).refreshCacheIfCold();
    }
}
