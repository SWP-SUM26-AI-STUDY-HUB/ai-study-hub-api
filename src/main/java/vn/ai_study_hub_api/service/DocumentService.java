package vn.ai_study_hub_api.service;

import org.springframework.web.multipart.MultipartFile;
import vn.ai_study_hub_api.controller.request.UpdateDocumentRequest;
import vn.ai_study_hub_api.controller.response.DocumentResponse;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentVisibility;
import java.io.File;
import java.util.List;
import java.util.UUID;

public interface DocumentService {
    DocumentResponse updateDocument(UUID documentId, UpdateDocumentRequest request, UUID userId);
    DocumentEntity initiateUpload(MultipartFile file, String title, List<Integer> tags, String description, DocumentVisibility visibility, UUID userId);

    void processDocumentAsync(UUID documentId, File tempFile, String storagePath, String contentType);

    void handleFastApiCallback(UUID documentId, String status, String summary);

    DocumentEntity generateShareLink(UUID documentId, UUID userId);

    DocumentEntity getSharedDocument(String token);
    List<DocumentResponse> getPersonalDocuments(UUID userId);

    /**
     * Search public documents by keyword.
     * Returns only active (COMPLETED), public, non-deleted documents matching
     * the keyword in title, description, summary, or tag labels.
     */
    List<DocumentResponse> searchPublicDocuments(String keyword);

    void deleteDocument(UUID documentId, UUID userId);

    vn.ai_study_hub_api.controller.response.DocumentAccessResponse getPreviewAccess(UUID documentId, vn.ai_study_hub_api.security.CustomUserDetails userDetails);
    DocumentResponse getDocumentById(UUID documentId, vn.ai_study_hub_api.security.CustomUserDetails userDetails);

    vn.ai_study_hub_api.controller.response.DocumentAccessResponse getDownloadAccess(UUID documentId, vn.ai_study_hub_api.security.CustomUserDetails userDetails);

    List<DocumentResponse> getPendingPublicDocuments();

    void approveDocument(UUID documentId);

    void rejectDocument(UUID documentId, String reason);

    org.springframework.data.domain.Page<DocumentResponse> getRecommendedDocuments(UUID userId, int page, int size);

    void triggerFastApiAsync(UUID documentId);

    void saveDocument(UUID documentId, UUID userId);

    void unsaveDocument(UUID documentId, UUID userId);

    org.springframework.data.domain.Page<DocumentResponse> getSavedDocuments(UUID userId, int page, int size);

    org.springframework.data.domain.Page<DocumentResponse> getPublicDocumentsByUser(UUID authorId, int page, int size);
}

