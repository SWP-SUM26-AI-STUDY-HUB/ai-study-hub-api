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
               description = "Returns the last scheduler-refreshed dashboard payload from the Redis cache "
                       + "(window = last 7 days). Never calls Langfuse on demand — a background scheduler "
                       + "(AiMetricsRefreshScheduler) refreshes it 6x/day so admin traffic stays within the "
                       + "Langfuse Hobby 100 Metrics API requests/day limit. Fails open (empty payload, never "
                       + "5xx) when Langfuse is unconfigured or the cache is cold.")
    public ApiResponse<AiMetricsResponse> getAiMetrics() {
        return ApiResponse.success(aiMetricsService.getDashboard(), "AI metrics retrieved successfully");
    }
}
