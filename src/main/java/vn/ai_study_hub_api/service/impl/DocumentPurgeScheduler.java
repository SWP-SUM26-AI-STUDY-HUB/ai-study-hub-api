package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.service.DocumentService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Permanently purges documents that have been soft-deleted for longer than the
 * configured retention period (default 30 days). Delegates the actual S3 + DB
 * removal to {@link DocumentService#hardDeleteDocument}, which runs in its own
 * transaction so a single document's failure does not roll back the rest of the
 * batch (the failed one stays soft-deleted and is retried on the next run).
 *
 * <p>Storage usage and RAG vectors are already reconciled at soft-delete time,
 * so this job only removes the lingering S3 files and the DB row.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentPurgeScheduler {

    private final DocumentRepository documentRepository;
    private final DocumentService documentService;

    @Value("${app.document.retention-days:30}")
    private int retentionDays;

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredDocuments() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        log.info("Running document purge for documents soft-deleted before {}", cutoff);

        List<DocumentEntity> expired = documentRepository.findSoftDeletedBefore(cutoff);

        if (expired.isEmpty()) {
            log.info("No documents to purge.");
            return;
        }

        log.info("Found {} document(s) to purge.", expired.size());

        int purged = 0;
        for (DocumentEntity document : expired) {
            try {
                documentService.hardDeleteDocument(document.getId());
                purged++;
            } catch (Exception e) {
                log.error("Failed to purge document {}: {}", document.getId(), e.getMessage(), e);
            }
        }

        log.info("Document purge completed. Purged {} of {} document(s).", purged, expired.size());
    }
}
