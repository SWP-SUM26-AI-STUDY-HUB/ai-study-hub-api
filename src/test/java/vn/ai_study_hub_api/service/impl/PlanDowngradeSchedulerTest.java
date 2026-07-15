package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlanDowngradeSchedulerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private PlanDowngradeScheduler scheduler;

    @Test
    void downgradeExpiredPlans_noExpiredUsers_helperNeverInvoked() {
        when(userRepository.findByPlanExpiresAtBeforeAndPlanIdNot(any(LocalDateTime.class), eq(1)))
                .thenReturn(List.of());

        scheduler.downgradeExpiredPlans();

        verify(userService, never()).downgradeToFreePlan(any(UserEntity.class));
    }

    @Test
    void downgradeExpiredPlans_expiredUsers_eachDowngraded() {
        UserEntity expired = UserEntity.builder()
                .id(UUID.randomUUID())
                .planId(2)
                .planExpiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(userRepository.findByPlanExpiresAtBeforeAndPlanIdNot(any(LocalDateTime.class), eq(1)))
                .thenReturn(List.of(expired));
        when(userService.downgradeToFreePlan(expired)).thenReturn(true);

        scheduler.downgradeExpiredPlans();

        verify(userService, times(1)).downgradeToFreePlan(expired);
    }
}
