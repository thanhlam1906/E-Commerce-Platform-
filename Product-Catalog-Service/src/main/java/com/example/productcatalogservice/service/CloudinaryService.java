package com.example.productcatalogservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String FOLDER = "products";

    public String upload(MultipartFile file) {
        validateFile(file);

        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", FOLDER,
                            "resource_type", "image"));
            return (String) result.get("secure_url");
        } catch (IOException e) {
            log.error("Upload ảnh thất bại", e);
            throw new RuntimeException("Không thể upload ảnh: " + e.getMessage());
        }
    }

    public void delete(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            log.error("Xóa ảnh thất bại: {}", publicId, e);
        }
    }

    public String extractPublicId(String imageUrl) {
        // URL format: https://res.cloudinary.com/<cloud>/image/upload/v1234567/products/abc123.jpg
        int uploadIndex = imageUrl.indexOf("/upload/");
        if (uploadIndex == -1) {
            return null;
        }
        String afterUpload = imageUrl.substring(uploadIndex + 8); // skip "/upload/"

        // Remove version prefix like "v1234567/"
        int versionEnd = afterUpload.indexOf('/');
        if (versionEnd != -1 && afterUpload.substring(0, versionEnd).startsWith("v")) {
            afterUpload = afterUpload.substring(versionEnd + 1);
        }

        // Remove file extension
        int dotIndex = afterUpload.lastIndexOf('.');
        return dotIndex != -1 ? afterUpload.substring(0, dotIndex) : afterUpload;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File ảnh trống");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File ảnh vượt quá 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Chỉ chấp nhận file ảnh (jpg, png, webp)");
        }
    }
}
