package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.controller.request.ReportRequest;
import vn.ai_study_hub_api.controller.response.ReportResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.model.NotificationEntity;
import vn.ai_study_hub_api.model.ReportEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserRole;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.NotificationRepository;
import vn.ai_study_hub_api.repository.ReportRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.repository.ViolationHistoryRepository;
import vn.ai_study_hub_api.service.ReportService;
import vn.ai_study_hub_api.controller.response.ReportedDocumentResponse;
import vn.ai_study_hub_api.controller.response.ReportDetailResponse;
import vn.ai_study_hub_api.model.ReportStatus;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final ViolationHistoryRepository violationHistoryRepository;

    @Override
    @Transactional
    public ReportResponse submitReport(UUID documentId, UUID reporterId, ReportRequest request) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "The document you are looking for does not exist."));

        if (!DocumentVisibility.PUBLIC.equals(document.getVisibility())) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "You can only report public documents.");
        }

        if (document.getDeletedAt() != null) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "This document has been deleted and cannot be reported.");
        }

        UserEntity reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Reporter not found."));

        ReportEntity report = ReportEntity.builder()
                .reporter(reporter)
                .document(document)
                .reason(request.getReason())
                .build();

        reportRepository.save(report);
        alertAdmins(document, reporter, request.getReason());

        return ReportResponse.builder()
                .reportId(report.getId())
                .documentId(documentId)
                .reason(report.getReason())
                .status(report.getStatus().name())
                .createdAt(report.getCreatedAt() != null ? report.getCreatedAt() : java.time.LocalDateTime.now())
                .build();
    }

    private void alertAdmins(DocumentEntity document, UserEntity reporter, String reason) {
        List<UserEntity> admins = userRepository.findAllByRole(UserRole.ADMIN);

        String reporterName = reporter.getFullName();
        if (reporterName == null || reporterName.isBlank()) {
            reporterName = reporter.getEmail();
        }

        String title = "New Abuse Report Submitted";
        String content = String.format(
                "Document '%s' has been reported by %s. Reason: %s",
                document.getTitle(), reporterName, reason
        );

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
    public void resolveReport(UUID reportId, String customReason) {
        ReportEntity report = reportRepository.findById(reportId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Report not found."));

        if (!vn.ai_study_hub_api.model.ReportStatus.PENDING.equals(report.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only pending reports can be resolved.");
        }

        DocumentEntity document = report.getDocument();
        UserEntity uploader = document.getUploader();

        // update lại trạng thái report
        report.setStatus(vn.ai_study_hub_api.model.ReportStatus.RESOLVED);
        reportRepository.save(report);

        // thay đổi trạng thái thành delete và update storage
        if (document.getDeletedAt() == null && !vn.ai_study_hub_api.model.DocumentStatus.DELETED.equals(document.getStatus())) {
            vn.ai_study_hub_api.model.DocumentStatus originalStatus = document.getStatus();
            document.setDeletedAt(java.time.LocalDateTime.now());
            document.setStatus(vn.ai_study_hub_api.model.DocumentStatus.DELETED);
            
            if (!vn.ai_study_hub_api.model.DocumentStatus.UPLOADING.equals(originalStatus)) {
                long newStorageUsed = Math.max(0L, uploader.getStorageUsed() - document.getFileSizeBytes());
                uploader.setStorageUsed(newStorageUsed);
                userRepository.save(uploader);
            }
            documentRepository.save(document);
        }

        String violationReason = (customReason != null && !customReason.trim().isEmpty())
                ? customReason.trim() 
                : report.getReason();

        vn.ai_study_hub_api.model.ViolationHistoryEntity violation = vn.ai_study_hub_api.model.ViolationHistoryEntity.builder()
                .user(uploader)
                .reason(violationReason)
                .status("WARNING")
                .build();
        violationHistoryRepository.save(violation);

        // gửi cảnh báo
        String notificationTitle = "Warning: Document Violation";
        String notificationContent = String.format(
                "Your document '%s' has been deleted due to a violation. Reason: %s",
                document.getTitle(), violationReason
        );

        NotificationEntity notification = NotificationEntity.builder()
                .user(uploader)
                .title(notificationTitle)
                .content(notificationContent)
                .isRead(false)
                .build();
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void rejectReport(UUID reportId) {
        ReportEntity report = reportRepository.findById(reportId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Report not found."));

        if (!vn.ai_study_hub_api.model.ReportStatus.PENDING.equals(report.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only pending reports can be rejected.");
        }

        report.setStatus(vn.ai_study_hub_api.model.ReportStatus.REJECTED);
        reportRepository.save(report);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportedDocumentResponse> getReportedDocuments() {
        return reportRepository.findReportedDocumentsSummary(ReportStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportDetailResponse> getReportDetailsForDocument(UUID documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "The document you are looking for does not exist.");
        }

        List<ReportEntity> reports = reportRepository.findReportsByDocumentIdAndStatus(documentId, ReportStatus.PENDING);
        return reports.stream()
                .map(r -> {
                    String reporterName = r.getReporter() != null ? r.getReporter().getFullName() : null;
                    if (reporterName == null || reporterName.isBlank()) {
                        reporterName = r.getReporter() != null ? r.getReporter().getEmail() : "Anonymous";
                    }
                    return ReportDetailResponse.builder()
                            .reportId(r.getId())
                            .reporterName(reporterName)
                            .reason(r.getReason())
                            .createdAt(r.getCreatedAt())
                            .build();
                })
                .collect(java.util.stream.Collectors.toList());
    }
}
