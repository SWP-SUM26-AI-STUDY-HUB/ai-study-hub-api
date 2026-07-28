package vn.ai_study_hub_api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.response.AdminDashboardStatsResponse;
import vn.ai_study_hub_api.controller.response.AiMetricsResponse;
import vn.ai_study_hub_api.service.AdminStatsService;
import vn.ai_study_hub_api.service.AiMetricsService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AdminStatsControllerTest {

    @Mock
    private AdminStatsService adminStatsService;

    @Mock
    private AiMetricsService aiMetricsService;

    @InjectMocks
    private AdminStatsController adminStatsController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getDashboardStats_Success() {
        // Arrange
        AdminDashboardStatsResponse mockResponse = AdminDashboardStatsResponse.builder()
                .totalUsers(100)
                .totalSuccessfulDocuments(50)
                .totalStorageUsedBytes(10485760L)
                .totalRevenueCurrentMonth(BigDecimal.valueOf(500.00))
                .signupStats(Collections.emptyList())
                .build();

        when(adminStatsService.getDashboardStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockResponse);

        // Act
        ApiResponse<AdminDashboardStatsResponse> response = adminStatsController.getDashboardStats(null, null);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("Dashboard statistics retrieved successfully", response.getMessage());
        assertEquals(100L, response.getData().getTotalUsers());
        assertEquals(50L, response.getData().getTotalSuccessfulDocuments());
        assertEquals(10485760L, response.getData().getTotalStorageUsedBytes());
        assertEquals(BigDecimal.valueOf(500.00), response.getData().getTotalRevenueCurrentMonth());

        verify(adminStatsService, times(1)).getDashboardStats(any(LocalDateTime.class), any(LocalDateTime.class));
    }
    @Test
    void getAiMetrics_returnsCachedDashboardFromService() {
        AiMetricsResponse mockResponse = AiMetricsResponse.empty("2026-07-12T00:00:00Z", "2026-07-19T00:00:00Z", false);
        when(aiMetricsService.getDashboard()).thenReturn(mockResponse);

        ApiResponse<AiMetricsResponse> response = adminStatsController.getAiMetrics();

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("AI metrics retrieved successfully", response.getMessage());
        assertFalse(response.getData().isConfigured());
        // Endpoint is cache-only — it delegates to getDashboard(), never fanning out to Langfuse.
        verify(aiMetricsService, times(1)).getDashboard();
    }
}
