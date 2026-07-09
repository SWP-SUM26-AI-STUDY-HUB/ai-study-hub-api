package vn.ai_study_hub_api.service.impl;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.ai_study_hub_api.controller.response.DocumentResponse;
import vn.ai_study_hub_api.controller.response.UploaderResponse;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.model.TagVisibility;
import vn.ai_study_hub_api.security.CustomUserDetails;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps {@link DocumentEntity} to {@link DocumentResponse}, applying per-viewer
 * tag visibility (a tag marked {@link TagVisibility#PRIVATE} is only surfaced
 * to its owner).
 *
 * <p>Replaces five near-identical inline mapping blocks that previously lived
 * in {@code DocumentServiceImpl} (DRY / Single Responsibility: this component
 * owns <em>read-side projection</em> only).</p>
 */
@Component
public class DocumentMapper {

    public DocumentResponse toResponse(DocumentEntity doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .fileName(doc.getTitle())
                .fileUrl(doc.getFileUrl())
                .fileSize(doc.getFileSizeBytes())
                .fileType(doc.getFileType())
                .status(doc.getStatus() != null ? doc.getStatus().name() : null)
                .description(doc.getDescription())
                .tags(getVisibleTags(doc))
                .uploader(toUploaderResponse(doc))
                .visibility(doc.getVisibility() != null ? doc.getVisibility().name() : null)
                .createdAt(doc.getCreatedAt())
                .build();
    }

    public UploaderResponse toUploaderResponse(DocumentEntity doc) {
        if (doc.getUploader() == null) {
            return null;
        }
        String fullName = doc.getUploader().getFullName();
        if (fullName == null || fullName.trim().isEmpty()) {
            fullName = doc.getUploader().getEmail();
        }
        return UploaderResponse.builder()
                .id(doc.getUploader().getId())
                .fullName(fullName)
                .avatarUrl(doc.getUploader().getAvatarUrl())
                .build();
    }

    /**
     * Tag id -&gt; label for tags the current viewer may see. {@code null} when the
     * document has no tags.
     */
    public Map<Integer, String> getVisibleTags(DocumentEntity doc) {
        if (doc.getTags() == null || doc.getTags().isEmpty()) {
            return null;
        }
        UUID currentUserId = getCurrentUserId();
        boolean isOwner = doc.getUploader() != null && doc.getUploader().getId().equals(currentUserId);
        return doc.getTags().stream()
                .filter(t -> isOwner || t.getVisibility() == null || TagVisibility.PUBLIC.equals(t.getVisibility()))
                .collect(Collectors.toMap(TagEntity::getId, TagEntity::getLabel));
    }

    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof CustomUserDetails) {
            return ((CustomUserDetails) authentication.getPrincipal()).getId();
        }
        return null;
    }
}
