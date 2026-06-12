package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import vn.ai_study_hub_api.controller.response.DocumentDetailResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.TagRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentEditServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private DocumentEditServiceImpl documentEditService;

    private UUID userId;
    private UUID documentId;
    private UserEntity owner;
    private DocumentEntity document;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        documentId = UUID.randomUUID();
        owner = UserEntity.builder().id(userId).email("owner@example.com").build();
        document = DocumentEntity.builder()
                .id(documentId)
                .uploader(owner)
                .title("My Document")
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .tags(new ArrayList<>())
                .build();
    }

    @Test
    void tagDocument_Success_ReusesAndCreatesTags() {
        TagEntity existingTag = TagEntity.builder().id(1).label("Existing").build();
        TagEntity newTag = TagEntity.builder().id(2).label("New").build();

        when(documentRepository.findByIdWithUploader(documentId)).thenReturn(Optional.of(document));
        when(tagRepository.findByLabel("Existing")).thenReturn(Optional.of(existingTag));
        when(tagRepository.findByLabel("New")).thenReturn(Optional.empty());
        when(tagRepository.save(any(TagEntity.class))).thenReturn(newTag);
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentDetailResponse response = documentEditService.tagDocument(documentId, userId, List.of("Existing", "New"));

        assertNotNull(response);
        assertEquals(2, response.getTags().size());
        assertTrue(response.getTags().contains("Existing"));
        assertTrue(response.getTags().contains("New"));

        verify(tagRepository, times(1)).findByLabel("Existing");
        verify(tagRepository, times(1)).findByLabel("New");
        verify(tagRepository, times(1)).save(any(TagEntity.class));
        verify(documentRepository, times(1)).save(document);
    }

    @Test
    void tagDocument_Failure_ForbiddenNonOwner() {
        UUID otherUserId = UUID.randomUUID();
        when(documentRepository.findByIdWithUploader(documentId)).thenReturn(Optional.of(document));

        AppException exception = assertThrows(AppException.class, () ->
                documentEditService.tagDocument(documentId, otherUserId, List.of("Tag"))
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void tagDocument_Failure_DeletedDocument() {
        document.setDeletedAt(LocalDateTime.now());
        when(documentRepository.findByIdWithUploader(documentId)).thenReturn(Optional.of(document));

        AppException exception = assertThrows(AppException.class, () ->
                documentEditService.tagDocument(documentId, userId, List.of("Tag"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void tagDocument_Failure_TooManyTags() {
        when(documentRepository.findByIdWithUploader(documentId)).thenReturn(Optional.of(document));

        AppException exception = assertThrows(AppException.class, () ->
                documentEditService.tagDocument(documentId, userId, List.of("T1", "T2", "T3", "T4", "T5", "T6"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("A document can have a maximum of 5 tags", exception.getMessage());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void tagDocument_Failure_TagTooLong() {
        when(documentRepository.findByIdWithUploader(documentId)).thenReturn(Optional.of(document));

        String longTag = "this-is-a-very-long-tag-exceeding-thirty-characters";
        AppException exception = assertThrows(AppException.class, () ->
                documentEditService.tagDocument(documentId, userId, List.of(longTag))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Tag length cannot exceed 30 characters", exception.getMessage());
        verify(documentRepository, never()).save(any());
    }
}
