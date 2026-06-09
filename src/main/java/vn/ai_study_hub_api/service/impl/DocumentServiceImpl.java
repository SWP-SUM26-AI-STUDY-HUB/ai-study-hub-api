package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.model.NotificationEntity;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.StoragePlanEntity;
import vn.ai_study_hub_api.model.UserRole;
import vn.ai_study_hub_api.model.UserStatus;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.NotificationRepository;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.repository.TagRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.DocumentService;
import vn.ai_study_hub_api.service.UploadProvider;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final UploadProvider uploadProvider;
    private final WebClient webClient;
    private final StoragePlanRepository storagePlanRepository;

    @Value("${fastapi.rag-process-url}")
    private String fastApiUrl;

    @Override
    @Transactional
    public DocumentEntity initiateUpload(MultipartFile file, String title, List<Integer> tagIds, String description, DocumentVisibility visibility, UUID userId) {
        log.info("Initiating upload for file: {}, user: {}, tags: {}, title: {}, visibility: {}", file.getOriginalFilename(), userId, tagIds, title, visibility);
 
        // Retrieve uploader user
        UserEntity uploader = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        // 1. Check user status
        if (UserStatus.OVERLIMITSTORAGE.equals(uploader.getStatus())) {
            throw new IllegalArgumentException("Your storage has exceeded the plan limit. Please delete files or upgrade your plan to upload");
        }

        // 2. Validate file format
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename).toLowerCase();
        List<String> allowedExtensions = List.of("pdf", "docx", "txt", "md");
        if (originalFilename == null || !allowedExtensions.contains(fileExtension)) {
            throw new IllegalArgumentException("Unsupported file format");
        }

        // 3. Validate storage limit
        Integer planId = uploader.getPlanId() != null ? uploader.getPlanId() : 1;
        StoragePlanEntity plan = storagePlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Storage plan not found with ID: " + planId));
        long limitInBytes = plan.getStorageLimit() * 1024L * 1024L * 1024L;
        if (uploader.getStorageUsed() + file.getSize() > limitInBytes) {
            throw new IllegalArgumentException("Upload failed: file size exceeds remaining storage quota");
        }

        // Retrieve and validate tags
        List<TagEntity> tags = tagRepository.findAllById(tagIds);
 
        // Pre-generate document ID for path consistency
        UUID documentId = UUID.randomUUID();
 
        // Generate storage path using uploadProvider (which formats as /{user_uuid}/{document_uuid}.{fileExtension})
        String storagePath = uploadProvider.generateStoragePath(userId, documentId, originalFilename);
 
        // Determine title
        String docTitle = (title != null && !title.trim().isEmpty()) ? title : originalFilename;
        if (docTitle == null || docTitle.isEmpty()) {
            docTitle = "untitled";
        }
 
        // Create document entity
        DocumentEntity document = DocumentEntity.builder()
                .id(documentId)
                .uploader(uploader)
                .title(docTitle)
                .fileUrl(storagePath) // Store storage path/key in file_url column
                .fileType(fileExtension)
                .fileSizeBytes(file.getSize())
                .status(DocumentStatus.UPLOADING)
                .description(description)
                .visibility(visibility != null ? visibility : DocumentVisibility.PRIVATE)
                .tags(tags)
                .build();

        return documentRepository.save(document);
    }

    @Async("taskExecutor")
    @Override
    public void processDocumentAsync(UUID documentId, File tempFile, String storagePath, String contentType) {
        log.info("Running background processing for document ID: {}, storagePath: {}", documentId, storagePath);
        try {
            // Upload file to the storage provider
            uploadProvider.upload(tempFile, storagePath, contentType);
            log.info("Successfully uploaded document {} to storage", documentId);

            // Fetch the document to check its public/private visibility status
            DocumentEntity document = documentRepository.findByIdWithUploader(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));

            // Update user storage usage
            UserEntity uploader = document.getUploader();
            if (uploader != null) {
                long newStorageUsed = uploader.getStorageUsed() + document.getFileSizeBytes();
                uploader.setStorageUsed(newStorageUsed);
                userRepository.save(uploader);
                log.info("Updated storage_used for user {} to {} bytes", uploader.getId(), newStorageUsed);
            }

            if (DocumentVisibility.PUBLIC.equals(document.getVisibility())) {
                // Public status -> auto switch to pending status
                document.setStatus(DocumentStatus.PENDING);
                documentRepository.save(document);
                log.info("Document ID {} is public. Status updated to PENDING.", documentId);
 
                // Insert a notification for all admin users
                createPendingApprovalNotifications(document);
            } else {
                // Private status -> auto switch to processing status (normal flow)
                document.setStatus(DocumentStatus.PROCESSING);
                documentRepository.save(document);
                log.info("Document ID {} is private. Status updated to PROCESSING.", documentId);

                // Generate temporary access URL
                String presignedUrl = uploadProvider.generatePresignedUrl(storagePath);
                log.info("Generated temporary access URL for document {}: {}", documentId, presignedUrl);

                // Send HTTP POST callback trigger to FastAPI
                log.info("Triggering FastAPI processing for document: {}", documentId);
                Map<String, String> payload = Map.of(
                        "document_id", documentId.toString(),
                        "file_url", presignedUrl
                );

                webClient.post()
                        .uri(fastApiUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .retrieve()
                        .toBodilessEntity()
                        .block(); // Synchronous block inside the async worker thread is safe

                log.info("FastAPI webhook successfully triggered for document ID: {}", documentId);
            }

        } catch (Exception e) {
            log.error("Failed to complete background processing for document ID: {}", documentId, e);
            // If error, update status to 'failed'
            updateDocumentStatus(documentId, DocumentStatus.FAILED);
        } finally {
            // Clean up the temporary file from the disk
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                log.debug("Cleaned up temporary file: {}, success: {}", tempFile.getAbsolutePath(), deleted);
            }
        }
    }

    private void createPendingApprovalNotifications(DocumentEntity document) {
        List<UserEntity> admins = userRepository.findAllByRole(UserRole.ADMIN);
        log.info("Creating pending approval notifications for {} admin(s) for document ID: {}", admins.size(), document.getId());
        
        String title = "New Document Pending Approval";
        String uploaderName = document.getUploader().getFullName();
        if (uploaderName == null || uploaderName.trim().isEmpty()) {
            uploaderName = document.getUploader().getEmail();
        }
        String content = String.format("Document '%s' uploaded by %s is waiting to approve.", document.getTitle(), uploaderName);

        for (UserEntity admin : admins) {
            NotificationEntity notification = NotificationEntity.builder()
                    .user(admin)
                    .title(title)
                    .content(content)
                    .isRead(false)
                    .build();
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public void handleFastApiCallback(UUID documentId, String status, String summary) {
        log.info("Processing callback from FastAPI. Document ID: {}, status: {}", documentId, status);

        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));

        if ("SUCCESS".equalsIgnoreCase(status)) {
            document.setSummary(summary);
            document.setStatus(DocumentStatus.COMPLETED);
            log.info("FastAPI succeeded. Document {} updated to COMPLETED with summary.", documentId);
        } else {
            document.setStatus(DocumentStatus.FAILED);
            log.warn("FastAPI failed. Document {} status updated to FAILED.", documentId);
        }

        documentRepository.save(document);
    }

    private void updateDocumentStatus(UUID documentId, DocumentStatus newStatus) {
        DocumentEntity document = documentRepository.findById(documentId).orElse(null);
        if (document != null) {
            document.setStatus(newStatus);
            documentRepository.save(document);
            log.info("Document ID {} status updated to {}", documentId, newStatus);
        } else {
            log.error("Could not update status to {} because document ID {} was not found", newStatus, documentId);
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
