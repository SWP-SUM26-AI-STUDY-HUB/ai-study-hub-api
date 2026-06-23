package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import vn.ai_study_hub_api.controller.response.DocumentResponse;
import vn.ai_study_hub_api.exception.AppException;
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
import vn.ai_study_hub_api.repository.ReviewRepository;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.repository.TagRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.DocumentService;
import vn.ai_study_hub_api.service.UploadProvider;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ReviewRepository reviewRepository;

    @Value("${fastapi.rag-process-url}")
    private String fastApiUrl;

    @Value("${app.upload.max-file-size-bytes}")
    private long maxFileSizeBytes;

    @Override
    @Transactional
    public DocumentEntity initiateUpload(MultipartFile file, String title, List<Integer> tags, String description, DocumentVisibility visibility, UUID userId) {
        log.info("Initiating upload for file: {}, user: {}, tags: {}, title: {}, visibility: {}", file.getOriginalFilename(), userId, tags, title, visibility);
 
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

        // 2b. Validate file size against the per-file limit (default 50MB)
        if (file.getSize() > maxFileSizeBytes) {
            long limitMb = maxFileSizeBytes / (1024L * 1024L);
            throw new IllegalArgumentException(
                    "Uploaded file size exceeds the " + limitMb + "MB limit. Please choose another file");
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
        List<TagEntity> tagEntities = new java.util.ArrayList<>();
        if (tags != null) {
            for (Integer tagId : tags) {
                if (tagId == null) {
                    continue;
                }
                TagEntity tagEntity = tagRepository.findById(tagId)
                        .orElseThrow(() -> new IllegalArgumentException("Tag not found with ID: " + tagId));
                tagEntities.add(tagEntity);
            }
        }
 
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
                .tags(tagEntities)
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

            if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
                log.info("Document ID {} has been deleted, skipping storage and FastAPI processing", documentId);
                return;
            }

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
                        .timeout(java.time.Duration.ofSeconds(10))
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
            if (DocumentStatus.PROCESSING.equals(document.getStatus())) {
                document.setStatus(DocumentStatus.COMPLETED);
                log.info("FastAPI succeeded. Document {} updated to COMPLETED with summary.", documentId);
            } else {
                log.info("FastAPI succeeded. Document {} summary updated, but status remains {} (not PROCESSING).", documentId, document.getStatus());
            }
        } else {
            if (DocumentStatus.PROCESSING.equals(document.getStatus())) {
                document.setStatus(DocumentStatus.FAILED);
                log.warn("FastAPI failed. Document {} status updated to FAILED.", documentId);
            } else {
                log.warn("FastAPI failed. Document {} status remains {} (not PROCESSING).", documentId, document.getStatus());
            }
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

    @Override
    @Transactional
    public DocumentEntity generateShareLink(UUID documentId, UUID userId) {
        log.info("Generating share link for document ID: {}, user ID: {}", documentId, userId);

        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        if (!document.getUploader().getId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not the owner of this document");
        }

        String token = "doc-" + UUID.randomUUID().toString();
        document.setLinkShare(token);

        return documentRepository.save(document);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentEntity getSharedDocument(String token) {
        log.info("Retrieving shared document for token: {}", token);

        DocumentEntity document = documentRepository.findByLinkShare(token)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Shared document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Shared document not found");
        }

        return document;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getPersonalDocuments(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new vn.ai_study_hub_api.exception.AppException(HttpStatus.NOT_FOUND, "User not found with ID: " + userId));

        if (vn.ai_study_hub_api.model.UserStatus.OVERLIMITSTORAGE.equals(user.getStatus())) {
            throw new vn.ai_study_hub_api.exception.AppException(HttpStatus.FORBIDDEN, "Your storage limit has been exceeded! Access denied.");
        }

        return documentRepository.findActiveDocumentsByUploaderId(userId)
                .stream()
                .map(doc -> {
                    vn.ai_study_hub_api.controller.response.UploaderResponse uploaderResponse = null;
                    if (doc.getUploader() != null) {
                        String finalUploaderName = doc.getUploader().getFullName();
                        if (finalUploaderName == null || finalUploaderName.trim().isEmpty()) {
                            finalUploaderName = doc.getUploader().getEmail();
                        }
                        uploaderResponse = vn.ai_study_hub_api.controller.response.UploaderResponse.builder()
                                .id(doc.getUploader().getId())
                                .fullName(finalUploaderName)
                                .avatarUrl(doc.getUploader().getAvatarUrl())
                                .build();
                    }

                    Map<Integer, String> tags = null;
                    if (doc.getTags() != null && !doc.getTags().isEmpty()) {
                        tags = doc.getTags().stream()
                                .collect(Collectors.toMap(t -> t.getId(), t -> t.getLabel()));
                    }

                    return DocumentResponse.builder()
                            .id(doc.getId())
                            .title(doc.getTitle())
                            .fileName(doc.getTitle())
                            .fileUrl(doc.getFileUrl())
                            .fileSize(doc.getFileSizeBytes())
                            .fileType(doc.getFileType())
                            .status(doc.getStatus() != null ? doc.getStatus().name() : null)
                            .description(doc.getDescription())
                            .tags(tags)
                            .uploader(uploaderResponse)
                            .visibility(doc.getVisibility() != null ? doc.getVisibility().name() : null)
                            .createdAt(doc.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Tìm kiếm tài liệu public theo keyword.
     *
     * Luồng xử lý:
     * 1. Validate keyword không trống
     * 2. Gọi repository search với điều kiện:
     *    - visibility = PUBLIC (chỉ tài liệu công khai)
     *    - status = COMPLETED (chỉ tài liệu đã xử lý xong, tức "active")
     *    - deleted_at IS NULL (loại bỏ tài liệu đã soft-delete)
     *    - keyword match trong title, description, summary, hoặc tag label
     * 3. Map kết quả sang DocumentResponse (bao gồm tags và uploaderName)
     */
    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> searchPublicDocuments(String keyword) {
        log.info("Searching public documents with keyword: '{}'", keyword);

        if (keyword == null || keyword.trim().isEmpty()) {
            log.warn("Search keyword is empty, returning empty result");
            return List.of();
        }

        String trimmedKeyword = keyword.trim();

        List<DocumentEntity> results = documentRepository.searchPublicDocuments(
                trimmedKeyword,
                DocumentVisibility.PUBLIC,
                DocumentStatus.COMPLETED
        );

        log.info("Found {} public documents matching keyword '{}'", results.size(), trimmedKeyword);

        if (results.isEmpty()) {
            throw new AppException(HttpStatus.NOT_FOUND, "No documents found matching the keyword.");
        }

        return results.stream()
                .map(doc -> {
                    // Lấy tên uploader (nếu có)
                    vn.ai_study_hub_api.controller.response.UploaderResponse uploaderResponse = null;
                    if (doc.getUploader() != null) {
                        String finalUploaderName = doc.getUploader().getFullName();
                        if (finalUploaderName == null || finalUploaderName.trim().isEmpty()) {
                            finalUploaderName = doc.getUploader().getEmail();
                        }
                        uploaderResponse = vn.ai_study_hub_api.controller.response.UploaderResponse.builder()
                                .id(doc.getUploader().getId())
                                .fullName(finalUploaderName)
                                .avatarUrl(doc.getUploader().getAvatarUrl())
                                .build();
                    }

                    // Lấy danh sách tag labels format id:label
                    Map<Integer, String> tags = null;
                    if (doc.getTags() != null && !doc.getTags().isEmpty()) {
                        tags = doc.getTags().stream()
                                .collect(Collectors.toMap(t -> t.getId(), t -> t.getLabel()));
                    }

                    return DocumentResponse.builder()
                            .id(doc.getId())
                            .title(doc.getTitle())
                            .fileName(doc.getTitle())
                            .fileUrl(doc.getFileUrl())
                            .fileSize(doc.getFileSizeBytes())
                            .fileType(doc.getFileType())
                            .status(doc.getStatus() != null ? doc.getStatus().name() : null)
                            .description(doc.getDescription())
                            .tags(tags)
                            .uploader(uploaderResponse)
                            .createdAt(doc.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public vn.ai_study_hub_api.controller.response.DocumentResponse updateDocument(UUID documentId, vn.ai_study_hub_api.controller.request.UpdateDocumentRequest request, UUID userId) {
        log.info("Updating document ID: {}, requested by user ID: {}", documentId, userId);

        DocumentEntity document = documentRepository.findByIdWithUploader(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        if (!document.getUploader().getId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not the owner of this document");
        }

        if (DocumentVisibility.PUBLIC.equals(document.getVisibility())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot edit public documents");
        }

        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            document.setTitle(request.getTitle().trim());
        }

        if (request.getDescription() != null) {
            document.setDescription(request.getDescription().trim());
        }

        if (request.getTags() != null) {
            List<TagEntity> tagEntities = new java.util.ArrayList<>();
            for (Integer tagId : request.getTags()) {
                TagEntity tagEntity = tagRepository.findById(tagId)
                        .orElseThrow(() -> new IllegalArgumentException("Tag not found with ID: " + tagId));
                tagEntities.add(tagEntity);
            }
            document.setTags(tagEntities);
        }

        boolean visibilityChanged = false;
        boolean needsRagProcessing = false;
        DocumentVisibility oldVisibility = document.getVisibility();
        
        if (request.getVisibility() != null && !request.getVisibility().trim().isEmpty()) {
            try {
                DocumentVisibility newVisibility = DocumentVisibility.valueOf(request.getVisibility().trim().toUpperCase());
                if (!oldVisibility.equals(newVisibility)) {
                    document.setVisibility(newVisibility);
                    visibilityChanged = true;
                    
                    if (DocumentVisibility.PUBLIC.equals(newVisibility)) {
                        // PRIVATE -> PUBLIC
                        document.setStatus(DocumentStatus.PENDING);
                        createPendingApprovalNotifications(document);
                    } else {
                        // PUBLIC -> PRIVATE
                        if (DocumentStatus.PENDING.equals(document.getStatus()) || DocumentStatus.REJECTED.equals(document.getStatus())) {
                            // Tài liệu chưa từng được RAG xử lý (vì chưa được duyệt)
                            document.setStatus(DocumentStatus.PROCESSING);
                            needsRagProcessing = true;
                        }
                        // Nếu đang là COMPLETED hoặc PROCESSING thì cứ giữ nguyên
                    }
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid visibility value: " + request.getVisibility());
            }
        }

        documentRepository.save(document);

        if (needsRagProcessing) {
            triggerFastApiAsync(documentId);
        } else if (visibilityChanged) {
            // Chỉ cập nhật metadata nếu tài liệu đã có vector trên RAG
            updateFastApiVisibilityAsync(documentId, document.getVisibility().name());
        }

        Map<Integer, String> updatedTags = document.getTags().stream()
                .collect(Collectors.toMap(TagEntity::getId, TagEntity::getLabel));

        vn.ai_study_hub_api.controller.response.UploaderResponse uploaderResponse = null;
        if (document.getUploader() != null) {
            uploaderResponse = vn.ai_study_hub_api.controller.response.UploaderResponse.builder()
                    .id(document.getUploader().getId())
                    .fullName(document.getUploader().getFullName())
                    .avatarUrl(document.getUploader().getAvatarUrl())
                    .build();
        }

        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .fileName(document.getTitle())
                .fileUrl(document.getFileUrl())
                .fileSize(document.getFileSizeBytes())
                .fileType(document.getFileType())
                .status(document.getStatus() != null ? document.getStatus().name() : null)
                .description(document.getDescription())
                .tags(updatedTags)
                .uploader(uploaderResponse)
                .createdAt(document.getCreatedAt())
                .visibility(document.getVisibility() != null ? document.getVisibility().name() : null)
                .build();
    }

    @Async("taskExecutor")
    public void updateFastApiVisibilityAsync(UUID documentId, String visibility) {
        log.info("Updating FastAPI visibility for document ID: {} to {}", documentId, visibility);
        try {
            Map<String, String> payload = Map.of(
                    "document_id", documentId.toString(),
                    "visibility", visibility
            );

            webClient.patch()
                    .uri(fastApiUrl + "/update-visibility")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(java.time.Duration.ofSeconds(10))
                    .block();
            log.info("Successfully updated visibility in FastAPI for document ID: {}", documentId);
        } catch (Exception e) {
            log.error("Failed to update visibility in FastAPI for document ID: {}", documentId, e);
        }
    }

    @Override
    @Transactional
    public void deleteDocument(UUID documentId, UUID userId) {
        log.info("Deleting document ID: {}, requested by user ID: {}", documentId, userId);

        DocumentEntity document = documentRepository.findByIdWithUploader(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        if (!document.getUploader().getId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not the owner of this document");
        }

        DocumentStatus originalStatus = document.getStatus();
        document.setDeletedAt(java.time.LocalDateTime.now());
        document.setStatus(DocumentStatus.DELETED);

        if (!DocumentStatus.UPLOADING.equals(originalStatus)) {
            UserEntity uploader = document.getUploader();
            long newStorageUsed = Math.max(0L, uploader.getStorageUsed() - document.getFileSizeBytes());
            uploader.setStorageUsed(newStorageUsed);
            userRepository.save(uploader);
            log.info("Subtracted {} bytes from user {} storage. New storage: {} bytes", 
                    document.getFileSizeBytes(), uploader.getId(), newStorageUsed);
        }

        documentRepository.save(document);
        
        deleteFastApiVectorsAsync(documentId);
    }

    @Async("taskExecutor")
    public void deleteFastApiVectorsAsync(UUID documentId) {
        log.info("Deleting FastAPI vectors for document ID: {}", documentId);
        try {
            String deleteUrl = fastApiUrl.replace("/process", "/delete/") + documentId;
            webClient.delete()
                    .uri(deleteUrl)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(java.time.Duration.ofSeconds(10))
                    .block();
            log.info("Successfully deleted vectors in FastAPI for document ID: {}", documentId);
        } catch (Exception e) {
            log.error("Failed to delete vectors in FastAPI for document ID: {}", documentId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public vn.ai_study_hub_api.controller.response.DocumentAccessResponse getPreviewAccess(UUID documentId, vn.ai_study_hub_api.security.CustomUserDetails userDetails) {
        log.info("Getting preview access for document ID: {}, user: {}", documentId, userDetails != null ? userDetails.getId() : "Guest");

        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        boolean hasAccess = false;

        if (DocumentVisibility.PUBLIC.equals(document.getVisibility()) && DocumentStatus.COMPLETED.equals(document.getStatus())) {
            hasAccess = true;
        } else {
            if (userDetails != null) {
                boolean isOwner = document.getUploader() != null && document.getUploader().getId().equals(userDetails.getId());
                boolean isAdmin = userDetails.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

                if (isOwner || isAdmin) {
                    hasAccess = true;
                }
            } else {
                throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
            }
        }

        if (!hasAccess) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied.");
        }

        String presignedUrl = uploadProvider.generatePresignedUrl(document.getFileUrl());

        String uploaderName = null;
        if (document.getUploader() != null) {
            uploaderName = document.getUploader().getFullName();
            if (uploaderName == null || uploaderName.trim().isEmpty()) {
                uploaderName = document.getUploader().getEmail();
            }
        }

        Double averageRating = reviewRepository.calculateAverageRating(documentId);
        double ratingVal = averageRating != null ? Math.round(averageRating * 10.0) / 10.0 : 0.0;

        long reviewCount = reviewRepository.countByDocumentId(documentId);

        List<String> tagsList = java.util.Collections.emptyList();
        if (document.getTags() != null) {
            tagsList = document.getTags().stream()
                    .map(vn.ai_study_hub_api.model.TagEntity::getLabel)
                    .collect(Collectors.toList());
        }

        return vn.ai_study_hub_api.controller.response.DocumentAccessResponse.builder()
                .documentId(document.getId())
                .title(document.getTitle())
                .fileType(document.getFileType())
                .fileSizeBytes(document.getFileSizeBytes())
                .presignedUrl(presignedUrl)
                .createdAt(document.getCreatedAt())
                .description(document.getDescription())
                .uploaderName(uploaderName)
                .rating(ratingVal)
                .reviewCount(reviewCount)
                .tags(tagsList)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public vn.ai_study_hub_api.controller.response.DocumentAccessResponse getDownloadAccess(UUID documentId, vn.ai_study_hub_api.security.CustomUserDetails userDetails) {
        log.info("Getting download access for document ID: {}, user: {}", documentId, userDetails != null ? userDetails.getId() : "Guest");

        if (userDetails == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }

        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        boolean hasAccess = false;

        if (DocumentVisibility.PUBLIC.equals(document.getVisibility()) && DocumentStatus.COMPLETED.equals(document.getStatus())) {
            hasAccess = true;
        } else {
            boolean isOwner = document.getUploader() != null && document.getUploader().getId().equals(userDetails.getId());
            boolean isAdmin = userDetails.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (isOwner || isAdmin) {
                hasAccess = true;
            }
        }

        if (!hasAccess) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied.");
        }

        String presignedUrl = uploadProvider.generatePresignedUrl(document.getFileUrl());

        return vn.ai_study_hub_api.controller.response.DocumentAccessResponse.builder()
                .documentId(document.getId())
                .title(document.getTitle())
                .fileType(document.getFileType())
                .fileSizeBytes(document.getFileSizeBytes())
                .presignedUrl(presignedUrl)
                .createdAt(document.getCreatedAt())
                .description(document.getDescription())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getPendingPublicDocuments() {
        log.info("Fetching pending public documents for moderation");
        List<DocumentEntity> pendingDocs = documentRepository.findPendingPublicDocuments(
                DocumentStatus.PENDING,
                DocumentVisibility.PUBLIC
        );
        return pendingDocs.stream()
                .map(doc -> {
                    vn.ai_study_hub_api.controller.response.UploaderResponse uploaderResponse = null;
                    if (doc.getUploader() != null) {
                        String finalUploaderName = doc.getUploader().getFullName();
                        if (finalUploaderName == null || finalUploaderName.trim().isEmpty()) {
                            finalUploaderName = doc.getUploader().getEmail();
                        }
                        uploaderResponse = vn.ai_study_hub_api.controller.response.UploaderResponse.builder()
                                .id(doc.getUploader().getId())
                                .fullName(finalUploaderName)
                                .avatarUrl(doc.getUploader().getAvatarUrl())
                                .build();
                    }

                    Map<Integer, String> tags = null;
                    if (doc.getTags() != null && !doc.getTags().isEmpty()) {
                        tags = doc.getTags().stream()
                                .collect(Collectors.toMap(t -> t.getId(), t -> t.getLabel()));
                    }

                    return DocumentResponse.builder()
                            .id(doc.getId())
                            .title(doc.getTitle())
                            .fileName(doc.getTitle())
                            .fileUrl(doc.getFileUrl())
                            .fileSize(doc.getFileSizeBytes())
                            .fileType(doc.getFileType())
                            .status(doc.getStatus() != null ? doc.getStatus().name() : null)
                            .description(doc.getDescription())
                            .tags(tags)
                            .uploader(uploaderResponse)
                            .visibility(doc.getVisibility() != null ? doc.getVisibility().name() : null)
                            .createdAt(doc.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void approveDocument(UUID documentId) {
        log.info("Approving public document with ID: {}", documentId);
        DocumentEntity document = documentRepository.findByIdWithUploader(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        if (!DocumentStatus.PENDING.equals(document.getStatus()) || !DocumentVisibility.PUBLIC.equals(document.getVisibility())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only pending public documents can be approved");
        }

        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);

        // taạo thông báo cho người up
        String title = "Document Approved";
        String content = String.format("Your document '%s' has been approved and is now public.", document.getTitle());
        NotificationEntity notification = NotificationEntity.builder()
                .user(document.getUploader())
                .title(title)
                .content(content)
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        log.info("Document {} successfully approved, status set to PROCESSING. Triggering FastAPI RAG index.", documentId);
        
        triggerFastApiAsync(documentId);
    }

    @Override
    @Transactional
    public void rejectDocument(UUID documentId, String reason) {
        log.info("Rejecting public document with ID: {}, reason: {}", documentId, reason);
        if (reason == null || reason.trim().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }

        DocumentEntity document = documentRepository.findByIdWithUploader(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        if (!DocumentStatus.PENDING.equals(document.getStatus()) || !DocumentVisibility.PUBLIC.equals(document.getVisibility())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only pending public documents can be rejected");
        }

        document.setStatus(DocumentStatus.REJECTED);
        document.setRejectionReason(reason.trim());
        documentRepository.save(document);

        // tạo thông báo cho người up
        String title = "Document Rejected";
        String content = String.format("Your document has been rejected. Reason: %s", reason.trim());
        NotificationEntity notification = NotificationEntity.builder()
                .user(document.getUploader())
                .title(title)
                .content(content)
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        log.info("Document {} successfully rejected, status set to REJECTED. Notification sent to owner.", documentId);
    }

    @Async("taskExecutor")
    @Override
    public void triggerFastApiAsync(UUID documentId) {
        log.info("Triggering FastAPI processing for document ID: {}", documentId);
        try {
            DocumentEntity document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));

            if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
                log.info("Document ID {} has been deleted, skipping FastAPI processing", documentId);
                return;
            }

            // tạo url truy cập tạm thời
            String presignedUrl = uploadProvider.generatePresignedUrl(document.getFileUrl());
            if (presignedUrl == null) {
                throw new IllegalStateException("Failed to generate presigned URL");
            }
            log.info("Generated temporary access URL for document {}: {}", documentId, presignedUrl);

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
                    .timeout(java.time.Duration.ofSeconds(10))
                    .block();

            log.info("FastAPI webhook successfully triggered for document ID: {}", documentId);
        } catch (Exception e) {
            log.error("Failed to trigger FastAPI for document ID: {}", documentId, e);
            updateDocumentStatus(documentId, DocumentStatus.FAILED);
        }
    }
}
