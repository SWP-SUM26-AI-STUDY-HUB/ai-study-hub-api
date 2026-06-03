package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import vn.ai_study_hub_api.exception.AppException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3UploadProviderTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @InjectMocks
    private S3UploadProvider s3UploadProvider;

    private final String BUCKET_NAME = "test-bucket";
    private File tempTestFile;

    @BeforeEach
    void setUp() throws IOException {
        ReflectionTestUtils.setField(s3UploadProvider, "bucketName", BUCKET_NAME);
        
        // Create a real temporary file to avoid NullPointerException in RequestBody.fromFile(file)
        tempTestFile = File.createTempFile("test-upload", ".txt");
        tempTestFile.deleteOnExit();
    }

    @AfterEach
    void tearDown() {
        if (tempTestFile != null && tempTestFile.exists()) {
            tempTestFile.delete();
        }
    }

    @Test
    void generateStoragePath_Success_WithExtension() {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        String originalFilename = "my_report.pdf";

        String storagePath = s3UploadProvider.generateStoragePath(userId, documentId, originalFilename);

        assertNotNull(storagePath);
        assertEquals(userId.toString() + "/" + documentId.toString() + ".pdf", storagePath);
    }

    @Test
    void generateStoragePath_Success_NoExtension() {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        String originalFilename = "my_file_no_extension";

        String storagePath = s3UploadProvider.generateStoragePath(userId, documentId, originalFilename);

        assertNotNull(storagePath);
        assertEquals(userId.toString() + "/" + documentId.toString(), storagePath);
    }

    @Test
    void upload_Success() {
        String storagePath = "user-id/file-uuid.pdf";
        String contentType = "application/pdf";

        PutObjectResponse putObjectResponse = PutObjectResponse.builder().build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(putObjectResponse);

        s3UploadProvider.upload(tempTestFile, storagePath, contentType);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(1)).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(BUCKET_NAME, capturedRequest.bucket());
        assertEquals(storagePath, capturedRequest.key());
        assertEquals(contentType, capturedRequest.contentType());
    }

    @Test
    void upload_Failure_ThrowsAppException() {
        String storagePath = "user-id/file-uuid.pdf";
        String contentType = "application/pdf";

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 Write Error"));

        AppException exception = assertThrows(AppException.class, () -> 
                s3UploadProvider.upload(tempTestFile, storagePath, contentType)
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
        assertTrue(exception.getMessage().contains("Failed to upload file to S3"));
    }

    @Test
    void generatePresignedUrl_Success() throws MalformedURLException {
        String storagePath = "user-id/file-uuid.pdf";
        String expectedUrl = "https://s3.amazonaws.com/test-bucket/user-id/file-uuid.pdf?token=abc";

        PresignedGetObjectRequest presignedGetObjectRequest = mock(PresignedGetObjectRequest.class);
        when(presignedGetObjectRequest.url()).thenReturn(new URL(expectedUrl));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGetObjectRequest);

        String resultUrl = s3UploadProvider.generatePresignedUrl(storagePath);

        assertEquals(expectedUrl, resultUrl);

        ArgumentCaptor<GetObjectPresignRequest> presignCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner, times(1)).presignGetObject(presignCaptor.capture());

        GetObjectPresignRequest capturedPresign = presignCaptor.getValue();
        assertEquals(BUCKET_NAME, capturedPresign.getObjectRequest().bucket());
        assertEquals(storagePath, capturedPresign.getObjectRequest().key());
    }

    @Test
    void generatePresignedUrl_Failure_ThrowsAppException() {
        String storagePath = "user-id/file-uuid.pdf";

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new RuntimeException("S3 Presign Error"));

        AppException exception = assertThrows(AppException.class, () -> 
                s3UploadProvider.generatePresignedUrl(storagePath)
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
        assertTrue(exception.getMessage().contains("Failed to generate file access link"));
    }
}
