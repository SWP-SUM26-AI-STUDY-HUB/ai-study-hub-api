package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.response.AdminDashboardStatsResponse;
import java.time.LocalDateTime;

public interface AdminStatsService {
    AdminDashboardStatsResponse getDashboardStats(LocalDateTime startDate, LocalDateTime endDate);
}
