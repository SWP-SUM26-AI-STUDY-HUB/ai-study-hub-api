package vn.ai_study_hub_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.model.ChatSessionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, UUID> {

    List<ChatSessionEntity> findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID userId);

    Optional<ChatSessionEntity> findByIdAndUserId(UUID id, UUID userId);
}
