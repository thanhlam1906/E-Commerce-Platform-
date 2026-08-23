package com.example.productcatalogservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudinaryServiceTest {

    @Test
    void extractPublicId_withVersion() {
        assertEquals("products/abc123", CloudinaryService.extractPublicId(
                "https://res.cloudinary.com/demo/image/upload/v1234567890/products/abc123.jpg"));
    }

    @Test
    void extractPublicId_withoutVersion() {
        assertEquals("products/abc123", CloudinaryService.extractPublicId(
                "https://res.cloudinary.com/demo/image/upload/products/abc123.png"));
    }

    @Test
    void extractPublicId_nestedPathAndJpeg() {
        assertEquals("products/sub/name", CloudinaryService.extractPublicId(
                "https://res.cloudinary.com/demo/image/upload/v123/products/sub/name.jpeg"));
    }

    @Test
    void extractPublicId_nonCloudinaryOrBlank_returnsNull() {
        assertNull(CloudinaryService.extractPublicId("https://example.com/image.jpg"));
        assertNull(CloudinaryService.extractPublicId(null));
        assertNull(CloudinaryService.extractPublicId(""));
    }

    @Test
    void validateFile_pngWithGenericContentType_passes() {
        byte[] pngBytes = {(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MultipartFile file = new MockMultipartFile("images_0", "photo.png", "application/octet-stream", pngBytes);
        CloudinaryService service = new CloudinaryService(null);
        assertDoesNotThrow(() -> service.validateFile(file));
    }

    @Test
    void validateFile_svgContent_rejectedByMagicBytes() {
        MultipartFile file = new MockMultipartFile("images_0", "evil.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes());
        CloudinaryService service = new CloudinaryService(null);
        assertThrows(IllegalArgumentException.class, () -> service.validateFile(file));
    }
}
