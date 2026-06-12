package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.controller.response.DocumentDetailResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.DocumentRepository;
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



    @Override
    @Transactional
    public DocumentDetailResponse tagDocument(UUID documentId, UUID userId, List<String> tags) {
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

        List<TagEntity> tagEntities = new java.util.ArrayList<>();
        if (tags != null) {
            if (tags.size() > 5) {
                throw new AppException(HttpStatus.BAD_REQUEST, "A document can have a maximum of 5 tags");
            }
            for (String tag : tags) {
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
        }
        document.setTags(tagEntities);

        documentRepository.save(document);
        return mapToResponse(document);
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
