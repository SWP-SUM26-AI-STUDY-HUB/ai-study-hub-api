package vn.ai_study_hub_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.controller.response.ReportedDocumentResponse;
import vn.ai_study_hub_api.model.ReportEntity;
import vn.ai_study_hub_api.model.ReportStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<ReportEntity, UUID> {

    @Query("SELECT new vn.ai_study_hub_api.controller.response.ReportedDocumentResponse(" +
           "d.id, d.title, u.fullName, COUNT(r), MAX(r.createdAt)) " +
           "FROM ReportEntity r " +
           "JOIN r.document d " +
           "LEFT JOIN d.uploader u " +
           "WHERE r.status = :status " +
           "GROUP BY d.id, d.title, u.fullName " +
           "ORDER BY COUNT(r) DESC")
    List<ReportedDocumentResponse> findReportedDocumentsSummary(@Param("status") ReportStatus status);

    @Query("SELECT r FROM ReportEntity r " +
           "JOIN FETCH r.reporter " +
           "WHERE r.document.id = :documentId AND r.status = :status " +
           "ORDER BY r.createdAt DESC")
    List<ReportEntity> findReportsByDocumentIdAndStatus(@Param("documentId") UUID documentId, @Param("status") ReportStatus status);
}
