package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.springframework.stereotype.Component;
import vn.ai_study_hub_api.service.UploadProvider;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Extracts embedded raster images from an uploaded document (PDF/DOCX) so {@link AutoModerationServiceImpl}
 * can classify them alongside the text chunks. Images are pulled from the original file in S3 and never
 * persisted — they exist only for the moderation call.
 *
 * <p>Scope: embedded images only (PDF XObjects, DOCX media). Text-as-image and full-page rendering are
 * intentionally out of scope. The per-document count is capped by the caller to bound cost. A whole-file
 * parse failure is rethrown so the caller can defer the document to manual review.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentImageExtractor {

    /** OpenAI accepts up to 20MB per image; cap raw bytes below that to leave headroom for base64 inflation. */
    static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    /** Skip decoded images above this many pixels to bound memory while re-encoding. */
    private static final int MAX_IMAGE_PIXELS = 25_000_000;

    private static final Map<String, String> SUPPORTED_MIME = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp");

    private final UploadProvider uploadProvider;

    /** Extracted image: raw bytes + MIME type suitable for a base64 data URL. */
    public record ExtractedImage(byte[] data, String mimeType) {}

    /**
     * Downloads the file from S3 and extracts up to {@code maxImages} unique embedded images.
     *
     * @throws RuntimeException if the file cannot be fetched or parsed (the caller treats this as
     *                          "images could not be checked" and defers to manual review).
     */
    public List<ExtractedImage> extract(String storagePath, String fileType, int maxImages) {
        if (storagePath == null || storagePath.isBlank()) {
            return List.of();
        }
        String ext = normalizeExt(fileType, storagePath);
        byte[] bytes = uploadProvider.download(storagePath);
        log.info("Extracting images from '{}' ({} bytes, type={}) for moderation", storagePath, bytes.length, ext);
        return switch (ext) {
            case "pdf" -> extractFromPdf(bytes, maxImages);
            case "docx" -> extractFromDocx(bytes, maxImages);
            default -> List.of();
        };
    }

    private List<ExtractedImage> extractFromPdf(byte[] bytes, int max) {
        List<ExtractedImage> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            for (PDPage page : doc.getPages()) {
                PDResources res = page.getResources();
                if (res == null) continue;
                for (COSName name : res.getXObjectNames()) {
                    if (out.size() >= max) return out;
                    try {
                        PDXObject xobj = res.getXObject(name);
                        if (!(xobj instanceof PDImageXObject img)) continue;
                        if ((long) img.getWidth() * img.getHeight() > MAX_IMAGE_PIXELS) continue;
                        byte[] jpg = toJpeg(img.getImage());
                        if (jpg == null || jpg.length > MAX_IMAGE_BYTES) continue;
                        if (!seen.add(sha256(jpg))) continue;
                        out.add(new ExtractedImage(jpg, "image/jpeg"));
                    } catch (IOException ex) {
                        log.debug("Skipping unreadable PDF image: {}", ex.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to parse PDF for images: {}", e.getMessage());
            throw new RuntimeException("PDF image extraction failed", e);
        }
        return out;
    }

    private List<ExtractedImage> extractFromDocx(byte[] bytes, int max) {
        List<ExtractedImage> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (XWPFPictureData pic : doc.getAllPictures()) {
                if (out.size() >= max) break;
                String ext = pic.suggestFileExtension();
                String mime = (ext == null) ? null : SUPPORTED_MIME.get(ext.toLowerCase(Locale.ROOT));
                if (mime == null) continue;
                byte[] data = pic.getData();
                if (data == null || data.length == 0 || data.length > MAX_IMAGE_BYTES) continue;
                if (!seen.add(sha256(data))) continue;
                out.add(new ExtractedImage(data, mime));
            }
        } catch (IOException e) {
            log.warn("Failed to parse DOCX for images: {}", e.getMessage());
            throw new RuntimeException("DOCX image extraction failed", e);
        }
        return out;
    }

    /** Re-encodes a decoded image to JPEG (compositing transparency onto white) to keep payloads small. */
    private byte[] toJpeg(BufferedImage src) throws IOException {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, src.getWidth(), src.getHeight());
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!ImageIO.write(out, "jpg", baos)) return null;
        return baos.toByteArray();
    }

    private static String normalizeExt(String fileType, String storagePath) {
        String raw = (fileType != null && !fileType.isBlank()) ? fileType : storagePath;
        int dot = raw.lastIndexOf('.');
        return (dot >= 0 ? raw.substring(dot + 1) : raw).toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JDK; this is purely defensive.
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }
}
