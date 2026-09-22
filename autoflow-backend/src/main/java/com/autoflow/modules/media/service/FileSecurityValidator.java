package com.autoflow.modules.media.service;

import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * High-security binary MIME validator and file security analyzer.
 * <p>
 * Does NOT rely solely on client-supplied Content-Type headers or file extensions.
 * Inspects binary magic numbers (file signatures), strips path traversal sequences,
 * verifies single safe extensions, and computes SHA-256 checksums during streaming.
 */
@Component
public class FileSecurityValidator {

    private static final int HEADER_READ_LIMIT = 512;

    // Disallowed dangerous extensions (executables, scripts, macro-enabled containers)
    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "sh", "bash", "php", "phtml", "php3", "php4", "php5",
            "pl", "cgi", "py", "rb", "js", "vbs", "jsp", "asp", "aspx", "jar", "war",
            "ear", "msi", "scr", "dll", "com", "bin", "ps1", "psm1"
    );

    // Whitelist of allowed MIME types for AutoFlow media assets
    private static final Map<String, Set<String>> MIME_TO_EXTENSIONS = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/gif", Set.of("gif"),
            "image/webp", Set.of("webp"),
            "application/pdf", Set.of("pdf"),
            "video/mp4", Set.of("mp4"),
            "video/quicktime", Set.of("mov")
    );

    public record ValidatedFileResult(
            String sanitizedFileName,
            String detectedMimeType,
            String fileExtension,
            long sizeBytes,
            String sha256Checksum,
            byte[] headerBytes
    ) {}

    /**
     * Inspects and validates an input stream against binary magic bytes and file security rules.
     *
     * @param originalFileName client declared filename
     * @param declaredMimeType client declared Content-Type header
     * @param inputStream      raw stream from file upload
     * @param maxSizeBytes     enforced maximum size in bytes
     * @return ValidatedFileResult containing detected MIME type, checksum, and sanitized filename
     */
    public ValidatedFileResult validate(
            String originalFileName,
            String declaredMimeType,
            InputStream inputStream,
            long maxSizeBytes
    ) throws IOException {
        String sanitizedName = sanitizeFilename(originalFileName);
        String extension = extractExtension(sanitizedName);

        // 1. Guard against forbidden extensions and double extension attacks
        if (FORBIDDEN_EXTENSIONS.contains(extension)) {
            throw new SecurityException("Upload rejected: forbidden executable or script extension ." + extension);
        }
        checkForDoubleExtension(sanitizedName);

        // 2. Read first 512 bytes for binary signature (magic bytes) inspection
        BufferedInputStream bufferedStream = new BufferedInputStream(inputStream, 8192);
        bufferedStream.mark(HEADER_READ_LIMIT + 1);

        byte[] headerBytes = new byte[HEADER_READ_LIMIT];
        int bytesRead = bufferedStream.read(headerBytes, 0, HEADER_READ_LIMIT);
        bufferedStream.reset();

        if (bytesRead < 4) {
            throw new IllegalArgumentException("Uploaded file is empty or too small to verify");
        }

        // 3. Detect actual binary MIME type from magic numbers
        String detectedMime = detectMimeFromMagicBytes(headerBytes, bytesRead);
        if (detectedMime == null) {
            throw new SecurityException("Upload rejected: unrecognized or unsupported binary file signature");
        }

        // 4. Verify detected MIME is in allowed whitelist
        Set<String> validExtensions = MIME_TO_EXTENSIONS.get(detectedMime);
        if (validExtensions == null || !validExtensions.contains(extension)) {
            throw new SecurityException(String.format(
                    "MIME spoofing detected: file content is '%s' but extension is '.%s'",
                    detectedMime, extension));
        }

        // 5. Compute SHA-256 and measure total size
        MessageDigest sha256Digest;
        try {
            sha256Digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }

        DigestInputStream digestStream = new DigestInputStream(bufferedStream, sha256Digest);
        byte[] buffer = new byte[8192];
        long totalBytes = 0;
        int read;

        while ((read = digestStream.read(buffer)) != -1) {
            totalBytes += read;
            if (totalBytes > maxSizeBytes) {
                throw new IllegalArgumentException(String.format(
                        "File size %d bytes exceeds maximum allowable limit of %d bytes", totalBytes, maxSizeBytes));
            }
        }

        String checksum = HexFormat.of().formatHex(sha256Digest.digest());

        return new ValidatedFileResult(
                sanitizedName,
                detectedMime,
                extension,
                totalBytes,
                checksum,
                Arrays.copyOf(headerBytes, bytesRead)
        );
    }

    /**
     * Inspects magic numbers in binary header.
     */
    public String detectMimeFromMagicBytes(byte[] header, int length) {
        if (length < 4) return null;

        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (length >= 8 &&
                (header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50 &&
                (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47 &&
                (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A &&
                (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A) {
            return "image/png";
        }

        // GIF: GIF87a or GIF89a (47 49 46 38)
        if ((header[0] & 0xFF) == 0x47 && (header[1] & 0xFF) == 0x49 &&
                (header[2] & 0xFF) == 0x46 && (header[3] & 0xFF) == 0x38) {
            return "image/gif";
        }

        // WebP: 'RIFF' .... 'WEBP'
        if (length >= 12 &&
                header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' &&
                header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }

        // PDF: %PDF- (25 50 44 46 2D)
        if (length >= 5 &&
                header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46 && header[4] == 0x2D) {
            return "application/pdf";
        }

        // MP4 / QuickTime: contains 'ftyp' at offset 4
        if (length >= 12 &&
                header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
            // Check for QuickTime specific brand 'qt  '
            if (header[8] == 'q' && header[9] == 't') {
                return "video/quicktime";
            }
            return "video/mp4";
        }

        return null;
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file_" + UUID.randomUUID();
        }
        // Remove path traversal and illegal chars
        String cleaned = filename.replace("\\", "/");
        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash >= 0) {
            cleaned = cleaned.substring(lastSlash + 1);
        }
        return cleaned.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void checkForDoubleExtension(String filename) {
        String[] parts = filename.split("\\.");
        if (parts.length > 2) {
            // Check if any intermediate part looks like a dangerous executable extension (e.g. test.php.png)
            for (int i = 1; i < parts.length - 1; i++) {
                if (FORBIDDEN_EXTENSIONS.contains(parts[i].toLowerCase(Locale.ROOT))) {
                    throw new SecurityException("Upload rejected: double extension containing dangerous type ." + parts[i]);
                }
            }
        }
    }
}
