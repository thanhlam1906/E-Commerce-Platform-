package com.voltstack.ecommerce.order.constant;

public final class ErrorMessages {

    private ErrorMessages() {}

    public static final String CART_EMPTY = "Giỏ hàng rỗng";
    public static final String ORDER_NOT_FOUND = "Đơn hàng không tìm thấy";
    public static final String ORDER_NOT_YOURS = "Không có quyền truy cập đơn hàng này";
    public static final String OUT_OF_STOCK = "Sản phẩm không đủ tồn kho";
    public static final String SKU_NOT_FOUND = "Sản phẩm không tồn tại";
    public static final String PRODUCT_UNAVAILABLE = "Không thể lấy thông tin sản phẩm, vui lòng thử lại";
    public static final String INVALID_ORDER_STATUS = "Trạng thái chuyển đổi không hợp lệ";
    public static final String INVALID_IDEMPOTENCY_KEY = "Idempotency-Key không hợp lệ";
    public static final String IDEMPOTENCY_KEY_EXPIRED = "Idempotency-Key đã dùng cho đơn bị hủy/hết hạn — dùng key mới";
    public static final String CHECKOUT_IN_PROGRESS = "Đang có giao dịch thanh toán cho tài khoản này, vui lòng thử lại";
    public static final String INVALID_ITEM_QUANTITY = "Số lượng không hợp lệ";
    public static final String PAYMENT_METHOD_UNSUPPORTED = "Phương thức thanh toán không được hỗ trợ";
    public static final String INVENTORY_NOT_FOUND = "Không tìm thấy tồn kho cho SKU này";
    public static final String VALIDATION_FAILED = "Dữ liệu không hợp lệ";
    public static final String INTERNAL_ERROR = "Lỗi hệ thống";
    public static final String UNAUTHENTICATED = "Chưa xác thực";
}
