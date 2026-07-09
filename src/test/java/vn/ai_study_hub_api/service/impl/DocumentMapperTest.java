package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.ai_study_hub_api.controller.response.DocumentResponse;
import vn.ai_study_hub_api.controller.response.UploaderResponse;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.model.TagVisibility;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.security.CustomUserDetails;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentMapperTest {

    private final DocumentMapper mapper = new DocumentMapper();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId) {
        CustomUserDetails details = new CustomUserDetails(
                userId, "viewer@example.com", "pwd", true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    @Test
    void toResponse_mapsCoreFieldsAndUploaderNameFallback() {
        LocalDateTime created = LocalDateTime.of(2026, 7, 7, 10, 0);
        UserEntity uploader = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .fullName("   ") // blank -> falls back to email
                .avatarUrl("https://avatar/x.png")
                .build();
        DocumentEntity doc = DocumentEntity.builder()
                .id(UUID.randomUUID())
                .uploader(uploader)
                .title("Calculus 101")
                .fileUrl("u/d.pdf")
                .fileType("pdf")
                .fileSizeBytes(2048L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PUBLIC)
                .description("A math doc")
                .createdAt(created)
                .build();

        DocumentResponse response = mapper.toResponse(doc);

        assertEquals(doc.getId(), response.getId());
        assertEquals("Calculus 101", response.getTitle());
        assertEquals("Calculus 101", response.getFileName());
        assertEquals("COMPLETED", response.getStatus());
        assertEquals("PUBLIC", response.getVisibility());
        assertEquals("A math doc", response.getDescription());
        assertEquals(created, response.getCreatedAt());
        UploaderResponse up = response.getUploader();
        assertNotNull(up);
        assertEquals("owner@example.com", up.getFullName()); // blank fullName -> email
        assertEquals("https://avatar/x.png", up.getAvatarUrl());
        assertNull(response.getTags()); // no tags
    }

    @Test
    void getVisibleTags_nonOwnerHidesPrivateTags() {
        TagEntity publicTag = TagEntity.builder().id(1).label("Math").visibility(TagVisibility.PUBLIC).build();
        TagEntity privateTag = TagEntity.builder().id(2).label("Secret").visibility(TagVisibility.PRIVATE).build();
        DocumentEntity doc = docWithTags(List.of(publicTag, privateTag));

        // No authenticated viewer -> isOwner=false -> private tag hidden.
        Map<Integer, String> tags = mapper.getVisibleTags(doc);

        assertEquals(1, tags.size());
        assertEquals("Math", tags.get(1));
        assertNull(tags.get(2));
    }

    @Test
    void getVisibleTags_ownerSeesPrivateTags() {
        UUID ownerId = UUID.randomUUID();
        TagEntity publicTag = TagEntity.builder().id(1).label("Math").visibility(TagVisibility.PUBLIC).build();
        TagEntity privateTag = TagEntity.builder().id(2).label("Secret").visibility(TagVisibility.PRIVATE).build();
        DocumentEntity doc = docWithUploaderAndTags(ownerId, List.of(publicTag, privateTag));
        authenticateAs(ownerId);

        Map<Integer, String> tags = mapper.getVisibleTags(doc);

        assertEquals(2, tags.size());
        assertTrue(tags.containsKey(2));
        assertEquals("Secret", tags.get(2));
    }

    @Test
    void getVisibleTags_nullTagsReturnsNull() {
        assertNull(mapper.getVisibleTags(DocumentEntity.builder().build()));
    }

    private DocumentEntity docWithTags(List<TagEntity> tags) {
        return docWithUploaderAndTags(UUID.randomUUID(), tags);
    }

    private DocumentEntity docWithUploaderAndTags(UUID uploaderId, List<TagEntity> tags) {
        return DocumentEntity.builder()
                .id(UUID.randomUUID())
                .uploader(UserEntity.builder().id(uploaderId).email("o@e.com").fullName("Owner").build())
                .title("t")
                .tags(tags)
                .build();
    }
}
