package com.voltstack.ecommerce.identity.constant;

public final class ErrorMessages {

    private ErrorMessages() {}

    public static final String EMAIL_EXISTS = "Email đã tồn tại";
    public static final String INVALID_CREDENTIALS = "Email hoặc mật khẩu không đúng";
    public static final String ACCOUNT_DISABLED = "Tài khoản đã bị khóa";
    public static final String USER_NOT_FOUND = "Người dùng không tìm thấy";
    public static final String ADDRESS_NOT_FOUND = "Địa chỉ không tìm thấy";
    public static final String INVALID_REFRESH_TOKEN = "Refresh token không hợp lệ";
    public static final String REFRESH_TOKEN_EXPIRED = "Refresh token đã hết hạn";
    public static final String TOKEN_REUSE_DETECTED = "Refresh token đã được sử dụng lại — vui lòng đăng nhập lại";
    public static final String UNAUTHENTICATED = "Chưa xác thực";
    public static final String VALIDATION_FAILED = "Dữ liệu không hợp lệ";
    public static final String INTERNAL_ERROR = "Lỗi hệ thống";
}
