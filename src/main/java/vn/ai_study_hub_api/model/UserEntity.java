package vn.ai_study_hub_api.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "role", nullable = false)
    @Builder.Default
    private String role = "user";

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "active";

    @Column(name = "plan_id")
    private Integer planId;

    @Column(name = "storage_used")
    @Builder.Default
    private Long storageUsed = 0L;

    @Column(name = "ai_requests_today")
    @Builder.Default
    private Integer aiRequestsToday = 0;

    @Column(name = "last_request_date")
    private LocalDate lastRequestDate;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "google_id", unique = true, length = 255)
    private String googleId;

    @Column(name = "plan_expires_at")
    private LocalDateTime planExpiresAt;

    /**
     * Get the UserRole enum representation.
     * @return UserRole enum
     */
    public UserRole getRoleEnum() {
        return role == null ? null : UserRole.valueOf(role.toLowerCase());
    }

    /**
     * Set the UserRole enum.
     * @param role UserRole enum
     */
    public void setRoleEnum(UserRole role) {
        this.role = role == null ? null : role.name().toLowerCase();
    }

    /**
     * Get the UserStatus enum representation.
     * @return UserStatus enum
     */
    public UserStatus getStatusEnum() {
        return status == null ? null : UserStatus.valueOf(status.toLowerCase());
    }

    /**
     * Set the UserStatus enum.
     * @param status UserStatus enum
     */
    public void setStatusEnum(UserStatus status) {
        this.status = status == null ? null : status.name().toLowerCase();
    }
}
