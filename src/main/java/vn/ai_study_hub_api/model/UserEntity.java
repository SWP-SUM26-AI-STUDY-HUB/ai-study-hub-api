package vn.ai_study_hub_api.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
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

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(
            read = "UPPER(role::text)",
            write = "cast(LOWER(?) as user_role)"
    )
    @Column(name = "role", nullable = false, columnDefinition = "user_role")
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(
            read = "UPPER(status::text)",
            write = "cast(LOWER(?) as user_status)"
    )
    @Column(name = "status", nullable = false, columnDefinition = "user_status")
    @Builder.Default
    private UserStatus status = UserStatus.INACTIVE;

    @Column(name = "plan_id")
    private Integer planId;

    @Column(name = "storage_used")
    @Builder.Default
    private Long storageUsed = 0L;

    @Column(name = "is_storage_counted")
    @Builder.Default
    private Boolean isStorageCounted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "google_id", unique = true, length = 255)
    private String googleId;

    @Column(name = "plan_expires_at")
    private LocalDateTime planExpiresAt;
}