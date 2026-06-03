package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.service.UploadProvider;

import java.io.File;
import java.time.Duration;
import java.util.UUID;

/**
 * Amazon S3 implementation of the UploadProvider.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3UploadProvider implements UploadProvider {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Override
    public void upload(File file, String storagePath, String contentType) {
        log.info("Uploading file to S3 bucket '{}' with key '{}'", bucketName, storagePath);
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .contentType(contentType)
                    // Note: Default settings preserve privacy (no public ACLs set).
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
            log.info("Successfully uploaded file to S3: {}", storagePath);
        } catch (Exception e) {
            log.error("Failed to upload file to S3 at path: {}", storagePath, e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload file to S3: " + e.getMessage());
        }
    }

    @Override
    public String generatePresignedUrl(String storagePath) {
        log.info("Generating S3 presigned URL for key '{}' in bucket '{}'", storagePath, bucketName);
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(10)) // Valid for 10 minutes
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedGetObjectRequest = s3Presigner.presignGetObject(presignRequest);
            String presignedUrl = presignedGetObjectRequest.url().toString();
            log.info("Generated S3 presigned URL: {}", presignedUrl);
            return presignedUrl;
        } catch (Exception e) {
            log.error("Failed to generate S3 presigned URL for path: {}", storagePath, e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate file access link: " + e.getMessage());
        }
    }

    @Override
    public String generateStoragePath(UUID userId, UUID documentId, String originalFilename) {
        String fileExtension = getFileExtension(originalFilename);
        // Generates path in format: {user_uuid}/{document_uuid}.{fileExtension}
        // Avoid leading slash for S3 standards.
        return userId.toString() + "/" + documentId.toString() + (fileExtension.isEmpty() ? "" : "." + fileExtension);
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
