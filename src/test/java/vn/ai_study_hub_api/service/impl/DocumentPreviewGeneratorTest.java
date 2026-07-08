package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import vn.ai_study_hub_api.service.UploadProvider;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

class DocumentPreviewGeneratorTest {

    private final UploadProvider uploadProvider = mock(UploadProvider.class);
    private final DocumentPreviewGenerator generator = new DocumentPreviewGenerator(uploadProvider);

    @TempDir
    Path tempDir;

    @Test
    void getPreviewStoragePath_insertsPreviewBeforeExtension() {
        assertEquals("u/d_preview.pdf", generator.getPreviewStoragePath("u/d.pdf"));
    }

    @Test
    void getPreviewStoragePath_noExtensionAppendsSuffix() {
        assertEquals("readme_preview", generator.getPreviewStoragePath("readme"));
    }

    @Test
    void getPreviewStoragePath_nullReturnsNull() {
        assertNull(generator.getPreviewStoragePath(null));
    }

    @Test
    void createAndUploadPreviewFile_txtTruncatesToThirtyPercentCappedAt100() throws Exception {
        // 250 lines -> ceil(30%) = 75, capped at 100 -> 75 lines kept.
        Path original = tempDir.resolve("doc.txt");
        Files.write(original,
                IntStream.range(0, 250).mapToObj(i -> "line " + i).toList(),
                StandardCharsets.UTF_8);

        // The temp preview file is deleted in createAndUploadPreviewFile's finally
        // block, so capture its content DURING the upload call (file still exists then).
        AtomicReference<List<String>> captured = new AtomicReference<>();
        Mockito.doAnswer(inv -> {
            captured.set(Files.readAllLines(((File) inv.getArgument(0)).toPath(), StandardCharsets.UTF_8));
            return null;
        }).when(uploadProvider).upload(any(File.class), eq("user/doc_preview.txt"), anyString());

        generator.createAndUploadPreviewFile(original.toFile(), "user/doc.txt", "text/plain");

        List<String> previewLines = captured.get();
        assertNotNull(previewLines);
        assertEquals(75, previewLines.size());
        assertEquals("line 0", previewLines.get(0));
    }

    @Test
    void createAndUploadPreviewFile_unsupportedExtensionUploadsOriginalToPreviewPath() throws Exception {
        Path original = tempDir.resolve("sheet.xlsx");
        Files.write(original, "binary-ish".getBytes(StandardCharsets.UTF_8));
        File originalFile = original.toFile();

        generator.createAndUploadPreviewFile(originalFile, "user/doc.xlsx", "application/vnd.ms-excel");

        // No truncation strategy for xlsx -> the original file is uploaded to the preview path verbatim.
        Mockito.verify(uploadProvider).upload(eq(originalFile), eq("user/doc_preview.xlsx"), eq("application/vnd.ms-excel"));
        Mockito.verify(uploadProvider, Mockito.never()).upload(any(), eq("user/doc.xlsx"), any());
    }

    @Test
    void createAndUploadPreviewFile_longLineIsWordCapped() throws Exception {
        Path original = tempDir.resolve("doc.txt");
        String longLine = IntStream.range(0, 300).mapToObj(i -> "w" + i).reduce((a, b) -> a + " " + b).orElse("");
        Files.write(original, List.of(longLine), StandardCharsets.UTF_8);

        AtomicReference<List<String>> captured = new AtomicReference<>();
        Mockito.doAnswer(inv -> {
            captured.set(Files.readAllLines(((File) inv.getArgument(0)).toPath(), StandardCharsets.UTF_8));
            return null;
        }).when(uploadProvider).upload(any(File.class), eq("user/doc_preview.txt"), anyString());

        generator.createAndUploadPreviewFile(original.toFile(), "user/doc.txt", "text/plain");

        List<String> previewLines = captured.get();
        assertNotNull(previewLines);
        assertEquals(1, previewLines.size());
        String line = previewLines.get(0);
        // First 200 words kept (w0..w199); 201st word (w200) dropped; notice appended.
        assertTrue(line.contains("w199"));
        assertTrue(!line.contains("w200"));
        assertTrue(line.contains("[Vui lòng đăng nhập để xem tiếp nội dung]"));
    }
}
