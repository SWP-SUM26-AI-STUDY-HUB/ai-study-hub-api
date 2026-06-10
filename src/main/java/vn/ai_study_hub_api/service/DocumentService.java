package vn.ai_study_hub_api.service;

import org.springframework.web.multipart.MultipartFile;
import vn.ai_study_hub_api.controller.response.DocumentAccessResponse;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.security.CustomUserDetails;
import java.io.File;
import java.util.List;
import java.util.UUID;

public interface DocumentService {
    DocumentEntity initiateUpload(MultipartFile file, String title, List<Integer> tagIds, String description, DocumentVisibility visibility, UUID userId);

    void processDocumentAsync(UUID documentId, File tempFile, String storagePath, String contentType);

    void handleFastApiCallback(UUID documentId, String status, String summary);

    DocumentAccessResponse getPreviewAccess(UUID documentId, CustomUserDetails userDetails);

    DocumentAccessResponse getDownloadAccess(UUID documentId, CustomUserDetails userDetails);
}
