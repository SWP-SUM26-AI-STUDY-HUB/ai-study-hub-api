package vn.ai_study_hub_api.controller.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AdminDashboardStatsResponse {
    private long totalUsers;
    private long totalSuccessfulDocuments;
    private long totalStorageUsedBytes;
    private BigDecimal totalRevenueCurrentMonth;
    private List<SignupStatsDto> signupStats;
}
