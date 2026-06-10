package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.controller.request.ReviewRequest;
import vn.ai_study_hub_api.controller.response.ReviewResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.model.ReviewEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.ReviewRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.ReviewService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ReviewResponse submitReview(UUID documentId, UUID userId, ReviewRequest request) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "The document you are looking for does not exist."));

        if (!DocumentVisibility.PUBLIC.equals(document.getVisibility())) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "You can only review public documents.");
        }

        if (!DocumentStatus.COMPLETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "This document is not yet approved for review.");
        }

        if (document.getDeletedAt() != null) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "This document has been deleted and cannot be reviewed.");
        }

        UserEntity reviewer = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "User not found."));

        ReviewEntity review = reviewRepository.findByUserIdAndDocumentId(userId, documentId)
                .orElse(ReviewEntity.builder()
                        .user(reviewer)
                        .document(document)
                        .build());

        review.setRating(request.getRating());
        review.setComment(request.getComment());
        reviewRepository.save(review);

        Double averageRating = reviewRepository.calculateAverageRating(documentId);
        double rounded = averageRating != null
                ? Math.round(averageRating * 10.0) / 10.0
                : request.getRating();

        String reviewerName = reviewer.getFullName();
        if (reviewerName == null || reviewerName.isBlank()) {
            reviewerName = reviewer.getEmail();
        }

        return ReviewResponse.builder()
                .reviewId(review.getId())
                .documentId(documentId)
                .reviewerName(reviewerName)
                .rating(request.getRating())
                .comment(request.getComment())
                .averageRating(rounded)
                .createdAt(review.getCreatedAt())
                .build();
    }
}
