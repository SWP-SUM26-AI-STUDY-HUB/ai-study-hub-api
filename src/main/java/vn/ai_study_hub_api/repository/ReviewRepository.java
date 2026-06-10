package vn.ai_study_hub_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.model.ReviewEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {

    Optional<ReviewEntity> findByUserIdAndDocumentId(UUID userId, UUID documentId);

    List<ReviewEntity> findByDocumentId(UUID documentId);

    @Query("SELECT AVG(r.rating) FROM ReviewEntity r WHERE r.document.id = :documentId")
    Double calculateAverageRating(@Param("documentId") UUID documentId);
}
