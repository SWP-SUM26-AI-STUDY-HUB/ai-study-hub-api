package vn.ai_study_hub_api.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.model.SavedDocumentEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedDocumentRepository extends JpaRepository<SavedDocumentEntity, UUID> {

    boolean existsByUserIdAndDocumentId(UUID userId, UUID documentId);

    Optional<SavedDocumentEntity> findByUserIdAndDocumentId(UUID userId, UUID documentId);

    Page<SavedDocumentEntity> findByUserId(UUID userId, Pageable pageable);

    void deleteByUserIdAndDocumentId(UUID userId, UUID documentId);
}
