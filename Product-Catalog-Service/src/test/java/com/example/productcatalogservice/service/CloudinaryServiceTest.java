package com.example.productcatalogservice.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
