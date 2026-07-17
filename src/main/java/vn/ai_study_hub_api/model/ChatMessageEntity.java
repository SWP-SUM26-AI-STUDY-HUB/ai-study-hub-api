package vn.ai_study_hub_api.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ChatSessionEntity session;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(
            read = "UPPER(sender::text)",
            write = "cast(LOWER(?) as message_sender)"
    )
    @Column(name = "sender", nullable = false, columnDefinition = "message_sender")
    private MessageSender sender;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "citations", columnDefinition = "jsonb")
    private String citations;

    /** JSONB payload for study-material (quiz/flashcard) bot messages: {@code {"type":"QUIZ"|"FLASHCARD","items":[...]}}. Null for regular chat messages. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "material_payload", columnDefinition = "jsonb")
    private String materialPayload;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        this.isNew = false;
    }
}
