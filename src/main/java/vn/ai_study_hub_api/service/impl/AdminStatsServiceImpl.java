package vn.ai_study_hub_api.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.controller.response.AdminDashboardStatsResponse;
import vn.ai_study_hub_api.controller.response.SignupStatsDto;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.InvoiceStatus;
import vn.ai_study_hub_api.service.AdminStatsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminStatsServiceImpl implements AdminStatsService {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public AdminDashboardStatsResponse getDashboardStats(LocalDateTime startDate, LocalDateTime endDate) {
        // 1. Thống kê tổng số lượng User
        Long totalUsers = entityManager.createQuery(
                "SELECT COUNT(u) FROM UserEntity u", Long.class)
                .getSingleResult();

        // 2. Thống kê tổng tài liệu thành công
        Long totalSuccessfulDocs = entityManager.createQuery(
                "SELECT COUNT(d) FROM DocumentEntity d WHERE d.status = :status", Long.class)
                .setParameter("status", DocumentStatus.COMPLETED)
                .getSingleResult();

        // 3. Thống kê dung lượng lưu trữ thực tế
        Long totalStorage = entityManager.createQuery(
                "SELECT COALESCE(SUM(d.fileSizeBytes), 0) FROM DocumentEntity d WHERE d.status = :status", Long.class)
                .setParameter("status", DocumentStatus.COMPLETED)
                .getSingleResult();

        // 4. Tính toán doanh thu tháng này
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal currentMonthRevenue = entityManager.createQuery(
                "SELECT COALESCE(SUM(i.amount), 0) FROM InvoiceEntity i WHERE i.status = :status AND i.createdAt >= :startDate", BigDecimal.class)
                .setParameter("status", InvoiceStatus.SUCCESS)
                .setParameter("startDate", startOfMonth)
                .getSingleResult();

        // 5. Thống kê đăng ký theo ngày trong khoảng thời gian được yêu cầu
        List<Object[]> rawStats = entityManager.createQuery(
                "SELECT CAST(u.createdAt AS date) as date, COUNT(u) as count " +
                "FROM UserEntity u " +
                "WHERE u.createdAt >= :startDate AND u.createdAt <= :endDate " +
                "GROUP BY CAST(u.createdAt AS date) " +
                "ORDER BY CAST(u.createdAt AS date) ASC", Object[].class)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getResultList();

        List<SignupStatsDto> signupStats = rawStats.stream()
                .map(row -> {
                    LocalDate date;
                    if (row[0] instanceof java.sql.Date) {
                        date = ((java.sql.Date) row[0]).toLocalDate();
                    } else if (row[0] instanceof java.time.LocalDate) {
                        date = (LocalDate) row[0];
                    } else {
                        date = LocalDate.parse(row[0].toString());
                    }
                    long count = ((Number) row[1]).longValue();
                    return new SignupStatsDto(date, count);
                })
                .collect(Collectors.toList());

        return AdminDashboardStatsResponse.builder()
                .totalUsers(totalUsers != null ? totalUsers : 0L)
                .totalSuccessfulDocuments(totalSuccessfulDocs != null ? totalSuccessfulDocs : 0L)
                .totalStorageUsedBytes(totalStorage != null ? totalStorage : 0L)
                .totalRevenueCurrentMonth(currentMonthRevenue != null ? currentMonthRevenue : BigDecimal.ZERO)
                .signupStats(signupStats)
                .build();
    }
}
