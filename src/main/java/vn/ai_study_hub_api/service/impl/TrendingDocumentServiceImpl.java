package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.controller.response.TagResponse;
import vn.ai_study_hub_api.controller.response.TrendingDocumentResponse;
import vn.ai_study_hub_api.controller.response.TrendingPage;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.TagVisibility;
import vn.ai_study_hub_api.repository.TrendingDocumentRepository;
import vn.ai_study_hub_api.service.TrendingDocumentService;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.ai_study_hub_api.security.CustomUserDetails;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrendingDocumentServiceImpl implements TrendingDocumentService {

    private final TrendingDocumentRepository trendingDocumentRepository;
    private final TrendingDocumentCacheLoader trendingDocumentCacheLoader;

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof CustomUserDetails) {
            return ((CustomUserDetails) authentication.getPrincipal()).getId();
        }
        return null;
    }

    /**
     * Serves the trending page from Redis (via {@link TrendingDocumentCacheLoader}) and then layers
     * the current viewer's own PRIVATE tags on top for documents they own. The cached payload is
     * identical for every viewer (PUBLIC tags only), so a single cache entry per page serves all
     * traffic; the owner enrichment is a cheap, request-scoped touch-up that preserves the original
     * "owners see all their tags" behavior without leaking private tags across users.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<TrendingDocumentResponse> getTrendingDocuments(Pageable pageable) {
        TrendingPage cached = trendingDocumentCacheLoader.loadTrendingPage(pageable);

        List<TrendingDocumentResponse> content = cached.getContent();
        if (content == null || content.isEmpty()) {
            return Page.empty(pageable);
        }

        enrichPrivateTagsForOwner(content);

        return new PageImpl<>(
                content,
                PageRequest.of(cached.getPageNumber(), cached.getPageSize()),
                cached.getTotalElements());
    }

    /**
     * For the docs on this page that the current user owns, replace the shared PUBLIC-only tag list
     * with their full tag set (public + private). No-op for guests and non-owners — the common case.
     */
    private void enrichPrivateTagsForOwner(List<TrendingDocumentResponse> content) {
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return;
        }

        List<UUID> docIds = content.stream()
                .map(TrendingDocumentResponse::getId)
                .collect(Collectors.toList());

        List<DocumentEntity> ownedDocs =
                trendingDocumentRepository.findOwnedDocumentsWithTags(docIds, currentUserId);
        if (ownedDocs.isEmpty()) {
            return;
        }

        Map<UUID, List<TagResponse>> fullTagsByDoc = ownedDocs.stream()
                .collect(Collectors.toMap(
                        DocumentEntity::getId,
                        this::mapAllTags,
                        (a, b) -> a));

        for (TrendingDocumentResponse response : content) {
            List<TagResponse> fullTags = fullTagsByDoc.get(response.getId());
            if (fullTags != null) {
                response.setTags(fullTags);
            }
        }
    }

    /** Owner sees every tag (public + private); visibility defaults to PUBLIC when null (legacy). */
    private List<TagResponse> mapAllTags(DocumentEntity doc) {
        if (doc.getTags() == null) {
            return Collections.emptyList();
        }
        return doc.getTags().stream()
                .map(tag -> TagResponse.builder()
                        .id(tag.getId())
                        .label(tag.getLabel())
                        .visibility(tag.getVisibility() != null
                                ? tag.getVisibility() : TagVisibility.PUBLIC)
                        .build())
                .collect(Collectors.toList());
    }
}
