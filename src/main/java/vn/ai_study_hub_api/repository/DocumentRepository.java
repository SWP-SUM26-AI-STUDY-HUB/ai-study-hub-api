package vn.ai_study_hub_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.model.DocumentEntity;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    @Query("SELECT d FROM DocumentEntity d JOIN FETCH d.uploader WHERE d.id = :id")
    Optional<DocumentEntity> findByIdWithUploader(@Param("id") UUID id);
    @Query("SELECT d FROM DocumentEntity d WHERE d.uploader.id = :uploaderId AND d.deletedAt IS NULL")
    java.util.List<DocumentEntity> findActiveDocumentsByUploaderId(@Param("uploaderId") UUID uploaderId);
}
