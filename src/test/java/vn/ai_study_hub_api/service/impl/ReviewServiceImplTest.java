package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ai_study_hub_api.controller.request.ReviewRequest;
import vn.ai_study_hub_api.controller.response.ReviewResponse;
import vn.ai_study_hub_api.model.*;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.NotificationRepository;
import vn.ai_study_hub_api.repository.ReviewRepository;
import vn.ai_study_hub_api.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Test
    void submitReview_otherUserReviews_notificationSentToUploader() {
        UUID documentId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();

        UserEntity uploader = UserEntity.builder()
                .id(uploaderId)
                .fullName("Document Owner")
                .email("owner@test.com")
                .build();

        UserEntity reviewer = UserEntity.builder()
                .id(reviewerId)
                .fullName("Reviewer Name")
                .email("reviewer@test.com")
                .build();

        DocumentEntity document = DocumentEntity.builder()
                .id(documentId)
                .title("Test Document")
                .visibility(DocumentVisibility.PUBLIC)
                .status(DocumentStatus.COMPLETED)
                .uploader(uploader)
                .build();

        ReviewRequest request = new ReviewRequest();
        request.setRating(5);
        request.setComment("Great document!");

        ReviewEntity savedReview = ReviewEntity.builder()
                .id(UUID.randomUUID())
                .user(reviewer)
                .document(document)
                .rating(5)
                .comment("Great document!")
                .build();

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));
        when(reviewRepository.findByUserIdAndDocumentId(reviewerId, documentId)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(ReviewEntity.class))).thenReturn(savedReview);
        when(reviewRepository.calculateAverageRating(documentId)).thenReturn(5.0);

        ReviewResponse response = reviewService.submitReview(documentId, reviewerId, request);

        assertNotNull(response);

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());

        NotificationEntity notification = captor.getValue();
        assertEquals("Bạn nhận được đánh giá mới", notification.getTitle());
        assertEquals(uploader, notification.getUser());
        assertTrue(notification.getContent().contains("Reviewer Name"));
        assertTrue(notification.getContent().contains("5⭐"));
        assertTrue(notification.getContent().contains("Test Document"));
        assertTrue(notification.getContent().contains("Great document!"));
    }

    @Test
    void submitReview_selfReview_noNotificationSent() {
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        UserEntity user = UserEntity.builder()
                .id(userId)
                .fullName("Self User")
                .email("self@test.com")
                .build();

        DocumentEntity document = DocumentEntity.builder()
                .id(documentId)
                .title("My Document")
                .visibility(DocumentVisibility.PUBLIC)
                .status(DocumentStatus.COMPLETED)
                .uploader(user)
                .build();

        ReviewRequest request = new ReviewRequest();
        request.setRating(4);
        request.setComment("My own review");

        ReviewEntity savedReview = ReviewEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .document(document)
                .rating(4)
                .comment("My own review")
                .build();

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reviewRepository.findByUserIdAndDocumentId(userId, documentId)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(ReviewEntity.class))).thenReturn(savedReview);
        when(reviewRepository.calculateAverageRating(documentId)).thenReturn(4.0);

        reviewService.submitReview(documentId, userId, request);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void submitReview_noComment_notificationWithoutComment() {
        UUID documentId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();

        UserEntity uploader = UserEntity.builder()
                .id(uploaderId)
                .fullName("Owner")
                .email("owner@test.com")
                .build();

        UserEntity reviewer = UserEntity.builder()
                .id(reviewerId)
                .fullName("Reviewer")
                .email("reviewer@test.com")
                .build();

        DocumentEntity document = DocumentEntity.builder()
                .id(documentId)
                .title("Doc Title")
                .visibility(DocumentVisibility.PUBLIC)
                .status(DocumentStatus.COMPLETED)
                .uploader(uploader)
                .build();

        ReviewRequest request = new ReviewRequest();
        request.setRating(3);
        request.setComment(null);

        ReviewEntity savedReview = ReviewEntity.builder()
                .id(UUID.randomUUID())
                .user(reviewer)
                .document(document)
                .rating(3)
                .build();

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));
        when(reviewRepository.findByUserIdAndDocumentId(reviewerId, documentId)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(ReviewEntity.class))).thenReturn(savedReview);
        when(reviewRepository.calculateAverageRating(documentId)).thenReturn(3.0);

        reviewService.submitReview(documentId, reviewerId, request);

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());

        NotificationEntity notification = captor.getValue();
        assertFalse(notification.getContent().contains("Nhận xét"));
    }
}
