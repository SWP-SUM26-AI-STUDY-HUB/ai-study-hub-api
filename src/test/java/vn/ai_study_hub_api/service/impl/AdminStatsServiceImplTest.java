package vn.ai_study_hub_api.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ai_study_hub_api.controller.response.AdminDashboardStatsResponse;
import vn.ai_study_hub_api.controller.response.SignupStatsDto;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminStatsServiceImplTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AdminStatsServiceImpl adminStatsService;

    @Mock
    private TypedQuery<Long> totalUsersQuery;

    @Mock
    private TypedQuery<Long> totalDocsQuery;

    @Mock
    private TypedQuery<Long> totalStorageQuery;

    @Mock
    private TypedQuery<BigDecimal> bigDecimalQuery;

    @Mock
    private TypedQuery<Object[]> objectArrayQuery;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @BeforeEach
    void setUp() {
        startDate = LocalDateTime.now().minusDays(30);
        endDate = LocalDateTime.now();
    }

    @Test
    void getDashboardStats_Success() {
        // Mock 1. Total users
        when(entityManager.createQuery("SELECT COUNT(u) FROM UserEntity u", Long.class)).thenReturn(totalUsersQuery);
        when(totalUsersQuery.getSingleResult()).thenReturn(10L);

        // Mock 2. Total successful docs
        when(entityManager.createQuery("SELECT COUNT(d) FROM DocumentEntity d WHERE d.status = :status", Long.class)).thenReturn(totalDocsQuery);
        when(totalDocsQuery.setParameter("status", DocumentStatus.COMPLETED)).thenReturn(totalDocsQuery);
        when(totalDocsQuery.getSingleResult()).thenReturn(5L);

        // Mock 3. Total storage
        when(entityManager.createQuery("SELECT COALESCE(SUM(d.fileSizeBytes), 0) FROM DocumentEntity d WHERE d.status = :status", Long.class)).thenReturn(totalStorageQuery);
        when(totalStorageQuery.setParameter("status", DocumentStatus.COMPLETED)).thenReturn(totalStorageQuery);
        when(totalStorageQuery.getSingleResult()).thenReturn(102400L);

        // Mock 4. Monthly revenue
        when(entityManager.createQuery("SELECT COALESCE(SUM(i.amount), 0) FROM InvoiceEntity i WHERE i.status = :status AND i.createdAt >= :startDate", BigDecimal.class)).thenReturn(bigDecimalQuery);
        when(bigDecimalQuery.setParameter(eq("status"), eq(InvoiceStatus.SUCCESS))).thenReturn(bigDecimalQuery);
        when(bigDecimalQuery.setParameter(eq("startDate"), any(LocalDateTime.class))).thenReturn(bigDecimalQuery);
        when(bigDecimalQuery.getSingleResult()).thenReturn(BigDecimal.valueOf(150.00));

        // Mock 5. Signup stats
        when(entityManager.createQuery(
                "SELECT CAST(u.createdAt AS date) as date, COUNT(u) as count " +
                "FROM UserEntity u " +
                "WHERE u.createdAt >= :startDate AND u.createdAt <= :endDate " +
                "GROUP BY CAST(u.createdAt AS date) " +
                "ORDER BY CAST(u.createdAt AS date) ASC", Object[].class)).thenReturn(objectArrayQuery);
        when(objectArrayQuery.setParameter("startDate", startDate)).thenReturn(objectArrayQuery);
        when(objectArrayQuery.setParameter("endDate", endDate)).thenReturn(objectArrayQuery);

        List<Object[]> rawStats = new ArrayList<>();
        rawStats.add(new Object[]{java.sql.Date.valueOf(LocalDate.now().minusDays(1)), 2L});
        rawStats.add(new Object[]{java.sql.Date.valueOf(LocalDate.now()), 3L});
        when(objectArrayQuery.getResultList()).thenReturn(rawStats);

        // Act
        AdminDashboardStatsResponse response = adminStatsService.getDashboardStats(startDate, endDate);

        // Assert
        assertNotNull(response);
        assertEquals(10L, response.getTotalUsers());
        assertEquals(5L, response.getTotalSuccessfulDocuments());
        assertEquals(102400L, response.getTotalStorageUsedBytes());
        assertEquals(BigDecimal.valueOf(150.00), response.getTotalRevenueCurrentMonth());
        
        List<SignupStatsDto> signupStats = response.getSignupStats();
        assertEquals(2, signupStats.size());
        assertEquals(LocalDate.now().minusDays(1), signupStats.get(0).getDate());
        assertEquals(2L, signupStats.get(0).getCount());
        assertEquals(LocalDate.now(), signupStats.get(1).getDate());
        assertEquals(3L, signupStats.get(1).getCount());
    }
}
