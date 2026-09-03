package com.voltstack.ecommerce.identity.service;

/** Thông tin user Google sau khi xác thực thành công (sub = Google subject id). */
public record GoogleUserInfo(String sub, String email, String fullName, String picture) {
}
