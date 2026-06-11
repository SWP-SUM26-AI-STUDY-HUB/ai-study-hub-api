package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.controller.request.UpdateDocumentRequest;
import vn.ai_study_hub_api.controller.response.DocumentDetailResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.model.NotificationEntity;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserRole;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.NotificationRepository;
import vn.ai_study_hub_api.repository.TagRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.DocumentEditService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentEditServiceImpl implements DocumentEditService {

    private final DocumentRepository documentRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public DocumentDetailResponse updateDocument(UUID documentId, UUID userId, UpdateDocumentRequest request) {
        DocumentEntity document = documentRepository.findByIdWithUploader(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "The document you are looking for does not exist."));

        if (!document.getUploader().getId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "You are not allowed to edit this document. Only the document owner can make changes.");
        }

        if (document.getDeletedAt() != null) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "This document has been deleted and can no longer be edited.");
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            document.setTitle(request.getTitle().trim());
        }

        if (request.getDescription() != null) {
            document.setDescription(request.getDescription());
        }

        if (request.getTags() != null) {
            List<TagEntity> tagEntities = new java.util.ArrayList<>();
            for (String tag : request.getTags()) {
                if (tag == null || tag.trim().isEmpty()) {
                    continue;
                }
                String trimmedTag = tag.trim();
                if (trimmedTag.length() > 30) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Tag length cannot exceed 30 characters");
                }
                TagEntity tagEntity = tagRepository.findByLabel(trimmedTag)
                        .orElseGet(() -> tagRepository.save(TagEntity.builder().label(trimmedTag).build()));
                tagEntities.add(tagEntity);
            }
            document.setTags(tagEntities);
        }

        if (request.getVisibility() != null) {
            DocumentVisibility newVisibility;
            try {
                newVisibility = DocumentVisibility.valueOf(request.getVisibility().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Invalid visibility value. Please use either 'PRIVATE' or 'PUBLIC'.");
            }

            boolean changingToPublic = DocumentVisibility.PUBLIC.equals(newVisibility)
                    && !DocumentVisibility.PUBLIC.equals(document.getVisibility());

            document.setVisibility(newVisibility);

            if (changingToPublic) {
                document.setStatus(DocumentStatus.PENDING);
                notifyAdmins(document);
            }
        }

        documentRepository.save(document);
        return mapToResponse(document);
    }

    private void notifyAdmins(DocumentEntity document) {
        List<UserEntity> admins = userRepository.findAllByRole(UserRole.ADMIN);
        String uploaderName = document.getUploader().getFullName();
        if (uploaderName == null || uploaderName.isBlank()) {
            uploaderName = document.getUploader().getEmail();
        }
        String notifTitle = "New Document Pending Approval";
        String notifContent = String.format("Document '%s' submitted by %s is awaiting your review.",
                document.getTitle(), uploaderName);

        for (UserEntity admin : admins) {
            NotificationEntity notification = NotificationEntity.builder()
                    .user(admin)
                    .title(notifTitle)
                    .content(notifContent)
                    .isRead(false)
                    .build();
            notificationRepository.save(notification);
        }
    }

    private DocumentDetailResponse mapToResponse(DocumentEntity document) {
        List<String> tagLabels = document.getTags() == null ? List.of()
                : document.getTags().stream()
                        .map(TagEntity::getLabel)
                        .collect(Collectors.toList());

        return DocumentDetailResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .description(document.getDescription())
                .visibility(document.getVisibility().name())
                .status(document.getStatus().name())
                .fileType(document.getFileType())
                .fileSizeBytes(document.getFileSizeBytes())
                .tags(tagLabels)
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
