package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.ai_study_hub_api.service.AiMetricsService;

/**
 * Keeps the admin AI/RAG dashboard cache warm on a fixed cadence so admin traffic
 * never hits Langfuse directly.
 *
 * <p><strong>Quota math.</strong> Langfuse Cloud's Hobby plan caps the Metrics API v2
 * at <strong>100 requests/day</strong>. One dashboard refresh fans out 15 queries
 * (9 widgets + 6 route-distribution queries — {@code metadata.*} cannot be grouped), so
 * this job fires 6×/day → 6 × 15 = 90 requests/day, leaving 10 of headroom for a
 * cold-start fill.
 *
 * <p>Fires at 06:00, 09:00, 12:00, 15:00, 18:00, 21:00 Asia/Ho_Chi_Minh — covering the
 * 06:00–24:00 admin-active window. {@code @EnableScheduling} lives in
 * {@code SchedulingConfig}. A single warm-up refresh on {@link ApplicationReadyEvent}
 * covers a cold cache after deploy (skipped when the cache survived the restart, so a
 * normal redeploy does not add a 7th Langfuse burst).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiMetricsRefreshScheduler {

    private final AiMetricsService aiMetricsService;

    /** 6 fires/day ICT × 15 Langfuse queries = 90 requests/day (< 100 Hobby limit). */
    @Scheduled(cron = "0 0 6,9,12,15,18,21 * * *", zone = "Asia/Ho_Chi_Minh")
    public void refreshAiMetricsCache() {
        log.info("Scheduled AI metrics cache refresh (Langfuse fan-out, 15 queries).");
        aiMetricsService.refreshCache();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        aiMetricsService.refreshCacheIfCold();
    }
}
