package com.example.productcatalogservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String FOLDER = "products";

    // Trích public_id từ secure_url Cloudinary (upload folder "products", resource_type image)
    private static final Pattern CLOUDINARY_UPLOAD_URL = Pattern.compile(
            "^https://res\\.cloudinary\\.com/[^/]+/image/upload/(?:v\\d+/)?(.+)\\.(?:jpg|jpeg|png|webp)$",
            Pattern.CASE_INSENSITIVE);

    // Magic bytes cho từng định dạng ảnh
    // ponytail: chỉ check 4 byte đầu, đủ để phân biệt JPEG/PNG/WebP. Nếu cần GIF thì thêm.
    private static boolean isAllowedImageType(byte[] header) {
        if (header.length < 4) return false;
        // JPEG: FF D8 FF
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) return true;
        // PNG: 89 50 4E 47
        if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E && header[3] == (byte) 0x47) return true;
        // WebP: 52 49 46 46 (RIFF)
        if (header[0] == (byte) 0x52 && header[1] == (byte) 0x49 && header[2] == (byte) 0x46 && header[3] == (byte) 0x46) return true;
        return false;
    }

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

    public void deleteImage(String secureUrl) {
        String publicId = extractPublicId(secureUrl);
        if (publicId == null) {
            log.warn("Bỏ qua xóa: URL không phải Cloudinary upload hợp lệ: {}", secureUrl);
            return;
        }
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId,
                    ObjectUtils.asMap("resource_type", "image", "invalidate", true));
            if (!"ok".equals(result.get("result"))) {
                log.warn("Cloudinary destroy trả '{}' cho publicId={}", result.get("result"), publicId);
            }
        } catch (IOException e) {
            log.error("Xóa ảnh Cloudinary thất bại (ảnh sẽ mồ côi): publicId={}", publicId, e);
        }
    }

    // package-private để test trực tiếp regex
    static String extractPublicId(String secureUrl) {
        if (secureUrl == null || secureUrl.isBlank()) return null;
        var matcher = CLOUDINARY_UPLOAD_URL.matcher(secureUrl.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    // package-private để test trực tiếp
    void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File ảnh trống");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File ảnh vượt quá 10MB");
        }

        // Không kiểm tra MIME header: client/gateway không giữ content-type đáng tin trên part
        // multipart (Postman gửi application/octet-stream, gateway có thể làm mất). Magic bytes
        // bên dưới là guard thật — chỉ chấp nhận header JPEG/PNG/WebP, reject SVG (XML text) và
        // mọi định dạng khác, nên vẫn chặn được XSS và MIME spoofing.
        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[4];
            int bytesRead = in.read(header);
            if (bytesRead < 4 || !isAllowedImageType(header)) {
                throw new IllegalArgumentException("File không phải ảnh hợp lệ (sai định dạng thực tế)");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Không thể đọc file ảnh");
        }
    }
}
