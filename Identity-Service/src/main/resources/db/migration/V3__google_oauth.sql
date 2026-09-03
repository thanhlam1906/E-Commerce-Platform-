-- Đăng nhập Google OAuth (Cách A — backend code flow).
-- User tạo qua Google có auth_provider='GOOGLE' + provider_id = Google subject (sub).
-- User đăng ký email/mật khẩu giữ auth_provider='LOCAL' (provider_id NULL).
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE users ADD COLUMN provider_id VARCHAR(255);

-- Một Google account chỉ link được một user. Local users để provider_id NULL
-- (UNIQUE cho phép nhiều NULL, không ảnh hưởng).
CREATE UNIQUE INDEX uq_users_google_provider_id ON users(provider_id) WHERE auth_provider = 'GOOGLE';
