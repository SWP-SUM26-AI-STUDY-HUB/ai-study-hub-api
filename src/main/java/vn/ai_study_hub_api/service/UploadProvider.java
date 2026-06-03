package vn.ai_study_hub_api.service;

import java.io.File;
import java.util.UUID;

public interface UploadProvider {
    void upload(File file, String storagePath, String contentType);

    String generatePresignedUrl(String storagePath);

    /**
     * Generates a unique, structured storage path/key for the file.
     * Format: {userId}/{documentId}.{fileExtension}
     *
     * @param userId           the UUID of the uploading user
     * @param documentId       the UUID of the document entity
     * @param originalFilename the original name of the uploaded file
     * @return the unique storage path/key string
     */
    String generateStoragePath(UUID userId, UUID documentId, String originalFilename);
}
