package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.response.NotificationResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.NotificationEntity;
import vn.ai_study_hub_api.repository.NotificationRepository;
import vn.ai_study_hub_api.security.CustomUserDetails;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications", description = "Endpoints for user notifications management")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get user notifications", description = "Retrieves a list of notifications for the currently authenticated user, ordered by creation date descending.")
    public ApiResponse<List<NotificationResponse>> getNotifications() {
        UUID userId = getCurrentUserId();
        log.info("Request to get notifications for user: {}", userId);
        
        List<NotificationEntity> notifications = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        
        List<NotificationResponse> response = notifications.stream()
                .map(n -> NotificationResponse.builder()
                        .id(n.getId())
                        .title(n.getTitle())
                        .content(n.getContent())
                        .isRead(n.getIsRead())
                        .type(n.getType())
                        .targetId(n.getTargetId())
                        .createdAt(n.getCreatedAt() != null ? n.getCreatedAt() : java.time.LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());
                
        return ApiResponse.success(response, "Notifications retrieved successfully");
    }

    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Mark a notification as read", description = "Marks a specific notification as read.")
    public ApiResponse<Void> markAsRead(@PathVariable("id") UUID id) {
        UUID userId = getCurrentUserId();
        log.info("Request to mark notification {} as read for user: {}", id, userId);
        
        NotificationEntity notification = notificationRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Notification not found"));
                
        if (!notification.getUser().getId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not authorized to access this notification");
        }
        
        notification.setIsRead(true);
        notificationRepository.save(notification);
        
        return ApiResponse.success("Notification marked as read successfully");
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getId();
    }
}
