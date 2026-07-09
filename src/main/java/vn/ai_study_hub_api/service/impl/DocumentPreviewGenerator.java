package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import vn.ai_study_hub_api.service.UploadProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * Generates truncated preview artifacts (roughly the first 30%) of an uploaded
 * document and uploads them alongside the original via the {@link UploadProvider}.
 *
 * <p>Pure file processing — no database, no document state machine. Extracted
 * from the former {@code DocumentServiceImpl} god class (Single Responsibility:
 * this component owns <em>preview rendering</em> only).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentPreviewGenerator {

    private static final int MAX_PREVIEW_PAGES = 50;
    private static final int MAX_PREVIEW_PARAGRAPHS = 50;
    private static final int MAX_PREVIEW_LINES = 100;
    private static final int MAX_WORDS_PER_BLOCK = 200;
    private static final String TRUNCATION_SUFFIX = "... [Vui lòng đăng nhập để xem tiếp nội dung]";

    private final UploadProvider uploadProvider;

    /**
     * Inserts {@code _preview} before the extension, e.g.
     * {@code u/d.pdf -> u/d_preview.pdf}.
     */
    public String getPreviewStoragePath(String storagePath) {
        if (storagePath == null) {
            return null;
        }
        int lastDot = storagePath.lastIndexOf('.');
        if (lastDot == -1) {
            return storagePath + "_preview";
        }
        return storagePath.substring(0, lastDot) + "_preview" + storagePath.substring(lastDot);
    }

    /**
     * Builds the truncated preview for the given original and uploads it. Falls
     * back to uploading the original verbatim on any failure (never throws).
     */
    public void createAndUploadPreviewFile(File originalFile, String originalStoragePath, String contentType) {
        String extension = "";
        int lastDot = originalStoragePath.lastIndexOf('.');
        if (lastDot != -1) {
            extension = originalStoragePath.substring(lastDot + 1).toLowerCase();
        }

        String previewStoragePath = getPreviewStoragePath(originalStoragePath);
        File tempPreviewFile = null;

        try {
            if ("pdf".equals(extension)) {
                tempPreviewFile = Files.createTempFile("preview-", ".pdf").toFile();
                truncatePdf(originalFile, tempPreviewFile);
                uploadProvider.upload(tempPreviewFile, previewStoragePath, contentType);
                log.info("Successfully generated and uploaded PDF preview for: {}", originalStoragePath);
            } else if ("docx".equals(extension)) {
                tempPreviewFile = Files.createTempFile("preview-", ".docx").toFile();
                truncateDocx(originalFile, tempPreviewFile);
                uploadProvider.upload(tempPreviewFile, previewStoragePath, contentType);
                log.info("Successfully generated and uploaded Word preview for: {}", originalStoragePath);
            } else if ("txt".equals(extension) || "md".equals(extension)) {
                tempPreviewFile = Files.createTempFile("preview-", "." + extension).toFile();
                truncateText(originalFile, tempPreviewFile);
                uploadProvider.upload(tempPreviewFile, previewStoragePath, contentType);
                log.info("Successfully generated and uploaded text/markdown preview for: {}", originalStoragePath);
            } else {
                uploadProvider.upload(originalFile, previewStoragePath, contentType);
                log.info("Unsupported preview format: {}. Uploaded original file to preview path.", extension);
            }
        } catch (Exception e) {
            log.error("Failed to create preview for {}. Falling back to uploading original file as preview.", originalStoragePath, e);
            try {
                uploadProvider.upload(originalFile, previewStoragePath, contentType);
            } catch (Exception uploadEx) {
                log.error("Failed to upload original file to preview path for {}", originalStoragePath, uploadEx);
            }
        } finally {
            if (tempPreviewFile != null && tempPreviewFile.exists()) {
                boolean deleted = tempPreviewFile.delete();
                log.debug("Cleaned up temp preview file: {}, success: {}", tempPreviewFile.getAbsolutePath(), deleted);
            }
        }
    }

    private void truncatePdf(File originalFile, File tempPreviewFile) throws Exception {
        try (PDDocument document = Loader.loadPDF(originalFile)) {
            int totalPages = document.getNumberOfPages();
            int pagesToKeep = Math.min(MAX_PREVIEW_PAGES, Math.max(1, (int) Math.ceil(totalPages * 0.3)));
            try (PDDocument previewDoc = new PDDocument()) {
                for (int i = 0; i < pagesToKeep; i++) {
                    previewDoc.addPage(document.getPage(i));
                }
                previewDoc.save(tempPreviewFile);
            }
        }
    }

    private void truncateDocx(File originalFile, File tempPreviewFile) throws Exception {
        try (FileInputStream fis = new FileInputStream(originalFile);
             XWPFDocument document = new XWPFDocument(fis)) {

            var paragraphs = document.getParagraphs();
            int paragraphsToKeep = Math.min(MAX_PREVIEW_PARAGRAPHS,
                    Math.max(1, (int) Math.ceil(paragraphs.size() * 0.3)));

            try (XWPFDocument previewDoc = new XWPFDocument();
                 FileOutputStream fos = new FileOutputStream(tempPreviewFile)) {
                for (int i = 0; i < paragraphsToKeep; i++) {
                    String originalText = paragraphs.get(i).getText();
                    previewDoc.createParagraph().createRun().setText(capWords(originalText));
                }
                previewDoc.write(fos);
            }
        }
    }

    private void truncateText(File originalFile, File tempPreviewFile) throws Exception {
        List<String> lines = Files.readAllLines(originalFile.toPath(), StandardCharsets.UTF_8);
        int linesToKeep = Math.min(MAX_PREVIEW_LINES, Math.max(1, (int) Math.ceil(lines.size() * 0.3)));

        List<String> previewLines = new java.util.ArrayList<>();
        for (int i = 0; i < linesToKeep; i++) {
            previewLines.add(capWords(lines.get(i)));
        }
        Files.write(tempPreviewFile.toPath(), previewLines, StandardCharsets.UTF_8);
    }

    /** Keeps the first {@value #MAX_WORDS_PER_BLOCK} words, appending a notice when truncated. */
    private String capWords(String text) {
        String[] words = text.split("\\s+");
        if (words.length > MAX_WORDS_PER_BLOCK) {
            return String.join(" ", Arrays.copyOfRange(words, 0, MAX_WORDS_PER_BLOCK)) + TRUNCATION_SUFFIX;
        }
        return text;
    }
}
