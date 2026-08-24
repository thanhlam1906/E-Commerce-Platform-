-- Token một lần cho xác thực email và đặt lại mật khẩu.
-- Lưu SHA-256 hash của token, không lưu plaintext. purpose phân biệt loại token.
CREATE TABLE verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    purpose VARCHAR(30) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_verification_tokens_purpose CHECK (purpose IN ('EMAIL_VERIFY', 'PASSWORD_RESET'))
);

CREATE INDEX idx_verification_tokens_user_id ON verification_tokens(user_id);
