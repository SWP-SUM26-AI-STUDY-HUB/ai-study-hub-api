package vn.ai_study_hub_api.service;

import java.io.File;
import java.util.UUID;

public interface UploadProvider {
    void upload(File file, String storagePath, String contentType);

    String generatePresignedUrl(String storagePath);

    void delete(String storagePath);

    String getPublicUrl(String storagePath);

    /**
     * Downloads the raw bytes of an object from S3 (used to extract embedded images for moderation).
     *
     * @param storagePath the S3 object key
     * @return the object's bytes
     */
    byte[] download(String storagePath);

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
