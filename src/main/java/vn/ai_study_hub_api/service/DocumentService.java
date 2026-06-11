package vn.ai_study_hub_api.service;

import org.springframework.web.multipart.MultipartFile;
import vn.ai_study_hub_api.controller.response.DocumentResponse;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentVisibility;
import java.io.File;
import java.util.List;
import java.util.UUID;

public interface DocumentService {
    DocumentEntity initiateUpload(MultipartFile file, String title, List<String> tags, String description, DocumentVisibility visibility, UUID userId);

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

    vn.ai_study_hub_api.controller.response.DocumentAccessResponse getDownloadAccess(UUID documentId, vn.ai_study_hub_api.security.CustomUserDetails userDetails);
}
