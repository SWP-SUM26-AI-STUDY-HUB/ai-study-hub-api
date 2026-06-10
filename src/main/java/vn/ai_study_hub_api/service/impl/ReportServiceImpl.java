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
import vn.ai_study_hub_api.service.ReportService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

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
                .createdAt(report.getCreatedAt())
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
}
