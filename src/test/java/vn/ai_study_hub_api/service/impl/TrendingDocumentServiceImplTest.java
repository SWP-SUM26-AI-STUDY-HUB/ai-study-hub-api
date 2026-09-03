package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.ai_study_hub_api.controller.response.TagResponse;
import vn.ai_study_hub_api.controller.response.TrendingDocumentResponse;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.model.TagVisibility;
import vn.ai_study_hub_api.repository.TrendingDocumentRepository;
import vn.ai_study_hub_api.security.CustomUserDetails;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the request-scoped layer of {@link TrendingDocumentServiceImpl}: cache loading is
 * mocked (the {@code @Cacheable} behavior on {@link TrendingDocumentCacheLoader} is exercised only
 * under a Spring context), so these tests pin down the owner-private-tag enrichment and the empty /
 * non-owner paths that wrap the cached payload.
 */
@ExtendWith(MockitoExtension.class)
public class TrendingDocumentServiceImplTest {

    @Mock
    private TrendingDocumentRepository trendingDocumentRepository;

    @Mock
    private TrendingDocumentCacheLoader trendingDocumentCacheLoader;

    @InjectMocks
    private TrendingDocumentServiceImpl trendingDocumentService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId) {
        CustomUserDetails principal = new CustomUserDetails(userId, "u@test.com", "x", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void getTrendingDocuments_guest_keepsCachedPublicTagsOnly() {
        UUID docId = UUID.randomUUID();
        TrendingDocumentResponse cached = TrendingDocumentResponse.builder()
                .id(docId).title("Doc")
                .tags(List.of(TagResponse.builder().id(1).label("pub").visibility(TagVisibility.PUBLIC).build()))
                .build();
        when(trendingDocumentCacheLoader.loadTrendingDocuments())
                .thenReturn(List.of(cached));

        // No authenticated user -> enrichment skipped entirely (guest hot path is cache-only).
        List<TrendingDocumentResponse> result =
                trendingDocumentService.getTrendingDocuments();

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getTags().size());
        verify(trendingDocumentRepository, never()).findOwnedDocumentsWithTags(anyList(), any());
    }

    @Test
    void getTrendingDocuments_owner_enrichedWithPrivateTags() {
        UUID ownerId = UUID.randomUUID();
        UUID ownedDocId = UUID.randomUUID();
        UUID otherDocId = UUID.randomUUID();
        authenticateAs(ownerId);

        TagResponse publicTag = TagResponse.builder().id(1).label("pub").visibility(TagVisibility.PUBLIC).build();
        TrendingDocumentResponse ownedResp = TrendingDocumentResponse.builder()
                .id(ownedDocId).title("Mine").tags(List.of(publicTag)).build();
        TrendingDocumentResponse otherResp = TrendingDocumentResponse.builder()
                .id(otherDocId).title("Theirs").tags(List.of(publicTag)).build();
        when(trendingDocumentCacheLoader.loadTrendingDocuments())
                .thenReturn(List.of(ownedResp, otherResp));

        // Owner owns only ownedDocId: its private "secret" tag must be merged on top of the public one.
        DocumentEntity ownedDoc = DocumentEntity.builder()
                .id(ownedDocId)
                .tags(List.of(
                        TagEntity.builder().id(1).label("pub").visibility(TagVisibility.PUBLIC).build(),
                        TagEntity.builder().id(2).label("secret").visibility(TagVisibility.PRIVATE).build()))
                .build();
        when(trendingDocumentRepository.findOwnedDocumentsWithTags(anyList(), eq(ownerId)))
                .thenReturn(List.of(ownedDoc));

        List<TrendingDocumentResponse> result =
                trendingDocumentService.getTrendingDocuments();

        List<TagResponse> ownedTags = result.stream()
                .filter(r -> r.getId().equals(ownedDocId)).findFirst().orElseThrow().getTags();
        assertEquals(2, ownedTags.size());
        assertTrue(ownedTags.stream().anyMatch(t -> "secret".equals(t.getLabel())));

        // Non-owned doc untouched: still public-only.
        List<TagResponse> otherTags = result.stream()
                .filter(r -> r.getId().equals(otherDocId)).findFirst().orElseThrow().getTags();
        assertEquals(1, otherTags.size());
        assertEquals("pub", otherTags.get(0).getLabel());
    }

    @Test
    void getTrendingDocuments_authenticatedNonOwner_keepsPublicTagsOnly() {
        UUID viewerId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        authenticateAs(viewerId);

        TrendingDocumentResponse cached = TrendingDocumentResponse.builder()
                .id(docId).title("Not mine")
                .tags(List.of(TagResponse.builder().id(1).label("pub").visibility(TagVisibility.PUBLIC).build()))
                .build();
        when(trendingDocumentCacheLoader.loadTrendingDocuments())
                .thenReturn(List.of(cached));
        // Viewer owns nothing on this page -> empty result, no tag replacement.
        when(trendingDocumentRepository.findOwnedDocumentsWithTags(anyList(), eq(viewerId)))
                .thenReturn(List.of());

        List<TrendingDocumentResponse> result =
                trendingDocumentService.getTrendingDocuments();

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getTags().size());
        assertEquals("pub", result.get(0).getTags().get(0).getLabel());
        verify(trendingDocumentRepository, times(1)).findOwnedDocumentsWithTags(anyList(), eq(viewerId));
    }

    @Test
    void getTrendingDocuments_emptyCache_returnsEmptyPage() {
        when(trendingDocumentCacheLoader.loadTrendingDocuments())
                .thenReturn(List.of());

        List<TrendingDocumentResponse> result =
                trendingDocumentService.getTrendingDocuments();

        assertTrue(result.isEmpty());
        verify(trendingDocumentRepository, never()).findOwnedDocumentsWithTags(anyList(), any());
    }
}
