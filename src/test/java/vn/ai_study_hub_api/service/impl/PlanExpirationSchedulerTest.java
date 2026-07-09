package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ai_study_hub_api.model.NotificationEntity;
import vn.ai_study_hub_api.model.StoragePlanEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.NotificationRepository;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlanExpirationSchedulerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private StoragePlanRepository storagePlanRepository;

    @InjectMocks
    private PlanExpirationScheduler scheduler;

    @Test
    void checkExpiringPlans_noExpiringUsers_noNotificationSent() {
        when(userRepository.findByPlanExpiresAtBetweenAndPlanIdIsNotNull(any(), any()))
                .thenReturn(Collections.emptyList());

        scheduler.checkExpiringPlans();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void checkExpiringPlans_userExpiringSoon_notificationCreated() {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.builder()
                .id(userId)
                .planId(2)
                .planExpiresAt(LocalDateTime.now().plusDays(2))
                .build();

        StoragePlanEntity plan = StoragePlanEntity.builder()
                .id(2)
                .name("Premium Plus")
                .build();

        when(userRepository.findByPlanExpiresAtBetweenAndPlanIdIsNotNull(any(), any()))
                .thenReturn(List.of(user));
        when(notificationRepository.existsByUserIdAndTitleAndCreatedAtAfter(eq(userId), any(), any()))
                .thenReturn(false);
        when(storagePlanRepository.findById(2)).thenReturn(Optional.of(plan));

        scheduler.checkExpiringPlans();

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());

        NotificationEntity notification = captor.getValue();
        assertEquals("Gói cước sắp hết hạn", notification.getTitle());
        assertEquals(user, notification.getUser());
        assertEquals("PLAN_EXPIRING", notification.getType());
        assertEquals("2", notification.getTargetId());
        assertFalse(notification.getIsRead());
        assertTrue(notification.getContent().contains("Premium Plus"));
        assertTrue(notification.getContent().contains("gia hạn"));
    }

    @Test
    void checkExpiringPlans_alreadyNotifiedToday_skipped() {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.builder()
                .id(userId)
                .planId(1)
                .planExpiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(userRepository.findByPlanExpiresAtBetweenAndPlanIdIsNotNull(any(), any()))
                .thenReturn(List.of(user));
        when(notificationRepository.existsByUserIdAndTitleAndCreatedAtAfter(eq(userId), any(), any()))
                .thenReturn(true);

        scheduler.checkExpiringPlans();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void checkExpiringPlans_multipleUsers_onlyNonNotifiedReceive() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        UserEntity user1 = UserEntity.builder()
                .id(userId1)
                .planId(2)
                .planExpiresAt(LocalDateTime.now().plusDays(2))
                .build();

        UserEntity user2 = UserEntity.builder()
                .id(userId2)
                .planId(3)
                .planExpiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(userRepository.findByPlanExpiresAtBetweenAndPlanIdIsNotNull(any(), any()))
                .thenReturn(List.of(user1, user2));
        when(notificationRepository.existsByUserIdAndTitleAndCreatedAtAfter(eq(userId1), any(), any()))
                .thenReturn(true);
        when(notificationRepository.existsByUserIdAndTitleAndCreatedAtAfter(eq(userId2), any(), any()))
                .thenReturn(false);
        when(storagePlanRepository.findById(3)).thenReturn(Optional.empty());

        scheduler.checkExpiringPlans();

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        NotificationEntity notification = captor.getValue();
        assertEquals(user2, notification.getUser());
        assertTrue(notification.getContent().contains("Premium"));
    }
}
