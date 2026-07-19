package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.response.AdminDashboardStatsResponse;
import vn.ai_study_hub_api.controller.response.AiMetricsResponse;
import vn.ai_study_hub_api.service.AdminStatsService;
import vn.ai_study_hub_api.service.AiMetricsService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Dashboard", description = "Endpoints for admin dashboard analytics and stats")
public class AdminStatsController {
    private final AdminStatsService adminStatsService;
    private final AiMetricsService aiMetricsService;

    @GetMapping("/stats")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get admin dashboard aggregation statistics",
               description = "Retrieves count of users, docs, storage size, monthly revenue and signup stats grouped by time.")
    public ApiResponse<AdminDashboardStatsResponse> getDashboardStats(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        // Mặc định thống kê đăng ký trong 30 ngày gần đây nếu không chỉ định khoảng thời gian
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        log.info("Admin request to retrieve dashboard statistics from {} to {}", startDate, endDate);
        AdminDashboardStatsResponse stats = adminStatsService.getDashboardStats(startDate, endDate);
        return ApiResponse.success(stats, "Dashboard statistics retrieved successfully");
    }

    @GetMapping("/ai-metrics")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get AI/RAG observability metrics from Langfuse for the admin dashboard",
               description = "Fans out Langfuse Metrics API v2 queries (latency, tokens, cost, citations, "
                       + "refusals, route distribution) for the given UTC window and returns one aggregated "
                       + "payload. Cached ~5 min. Fails open (empty payload, never 5xx) when Langfuse is "
                       + "unconfigured or unreachable.")
    public ApiResponse<AiMetricsResponse> getAiMetrics(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        // Default window = last 7 days, truncated to the minute so repeated dashboard
        // refreshes within the same minute share a Redis cache entry.
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
        if (from == null) {
            from = now.minusDays(7);
        }
        if (to == null) {
            to = now;
        }
        String fromTs = DateTimeFormatter.ISO_INSTANT.format(from.toInstant(ZoneOffset.UTC));
        String toTs = DateTimeFormatter.ISO_INSTANT.format(to.toInstant(ZoneOffset.UTC));

        log.info("Admin request for AI metrics from {} to {}", fromTs, toTs);
        return ApiResponse.success(aiMetricsService.getAiMetrics(fromTs, toTs), "AI metrics retrieved successfully");
    }
}
