package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.service.DocumentService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentPurgeSchedulerTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private DocumentPurgeScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 30);
    }

    @Test
    void purgeExpiredDocuments_noExpiredDocuments_serviceNeverInvoked() {
        when(documentRepository.findSoftDeletedBefore(any(LocalDateTime.class))).thenReturn(List.of());

        scheduler.purgeExpiredDocuments();

        verify(documentService, never()).hardDeleteDocument(any(UUID.class));
    }

    @Test
    void purgeExpiredDocuments_allDocumentsPurged() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        DocumentEntity doc1 = DocumentEntity.builder().id(id1).build();
        DocumentEntity doc2 = DocumentEntity.builder().id(id2).build();

        when(documentRepository.findSoftDeletedBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(doc1, doc2));

        scheduler.purgeExpiredDocuments();

        verify(documentService, times(1)).hardDeleteDocument(id1);
        verify(documentService, times(1)).hardDeleteDocument(id2);
    }

    @Test
    void purgeExpiredDocuments_oneFails_othersStillProcessed() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        DocumentEntity doc1 = DocumentEntity.builder().id(id1).build();
        DocumentEntity doc2 = DocumentEntity.builder().id(id2).build();

        when(documentRepository.findSoftDeletedBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(doc1, doc2));
        doThrow(new RuntimeException("S3 down")).when(documentService).hardDeleteDocument(id1);

        scheduler.purgeExpiredDocuments();

        verify(documentService, times(1)).hardDeleteDocument(id1);
        verify(documentService, times(1)).hardDeleteDocument(id2);
    }
}
