package com.autoflow.modules.media.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FileSecurityValidatorTest {

    private FileSecurityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileSecurityValidator();
    }

    @Test
    @DisplayName("Valid JPEG file with FF D8 FF header passes binary validation")
    void testValidJpeg() throws IOException {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46};
        var result = validator.validate("photo.jpg", "image/jpeg", new ByteArrayInputStream(jpegBytes), 1024 * 1024);

        assertNotNull(result);
        assertEquals("image/jpeg", result.detectedMimeType());
        assertEquals("jpg", result.fileExtension());
        assertEquals("photo.jpg", result.sanitizedFileName());
        assertEquals(jpegBytes.length, result.sizeBytes());
        assertNotNull(result.sha256Checksum());
    }

    @Test
    @DisplayName("Valid PNG file with 89 50 4E 47 header passes binary validation")
    void testValidPng() throws IOException {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};
        var result = validator.validate("banner.png", "image/png", new ByteArrayInputStream(pngBytes), 1024 * 1024);

        assertEquals("image/png", result.detectedMimeType());
        assertEquals("png", result.fileExtension());
    }

    @Test
    @DisplayName("Valid PDF document with %PDF- header passes binary validation")
    void testValidPdf() throws IOException {
        byte[] pdfBytes = "%PDF-1.7 header and sample stream bytes here".getBytes();
        var result = validator.validate("guide.pdf", "application/pdf", new ByteArrayInputStream(pdfBytes), 1024 * 1024);

        assertEquals("application/pdf", result.detectedMimeType());
        assertEquals("pdf", result.fileExtension());
    }

    @Test
    @DisplayName("MIME spoofing: PDF file disguised as .jpg is detected and rejected")
    void testMimeSpoofingRejected() {
        byte[] pdfBytes = "%PDF-1.7 disguised as image".getBytes();

        SecurityException ex = assertThrows(SecurityException.class, () ->
                validator.validate("malicious.jpg", "image/jpeg", new ByteArrayInputStream(pdfBytes), 1024 * 1024)
        );
        assertTrue(ex.getMessage().contains("MIME spoofing detected"));
    }

    @Test
    @DisplayName("Dangerous executable extension (.exe) is rejected regardless of content")
    void testForbiddenExtensionRejected() {
        byte[] bytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

        SecurityException ex = assertThrows(SecurityException.class, () ->
                validator.validate("trojan.exe", "image/jpeg", new ByteArrayInputStream(bytes), 1024 * 1024)
        );
        assertTrue(ex.getMessage().contains("forbidden executable or script extension"));
    }

    @Test
    @DisplayName("Double extension attack (photo.php.jpg) is detected and rejected")
    void testDoubleExtensionAttackRejected() {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};

        SecurityException ex = assertThrows(SecurityException.class, () ->
                validator.validate("photo.php.jpg", "image/jpeg", new ByteArrayInputStream(jpegBytes), 1024 * 1024)
        );
        assertTrue(ex.getMessage().contains("double extension containing dangerous type .php"));
    }

    @Test
    @DisplayName("File exceeding maxSizeBytes throws IllegalArgumentException")
    void testFileSizeExceeded() {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x01, 0x02, 0x03, 0x04};

        assertThrows(IllegalArgumentException.class, () ->
                validator.validate("large.jpg", "image/jpeg", new ByteArrayInputStream(jpegBytes), 4) // max 4 bytes
        );
    }
}
