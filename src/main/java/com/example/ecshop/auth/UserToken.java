package com.example.ecshop.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * メール確認・パスワード再設定のトークン（user_tokens テーブル）。
 * トークン本体は保存せず、SHA-256 のハッシュだけを持つ。
 */
@Entity
@Table(name = "user_tokens")
public class UserToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 会員エンティティそのものではなく ID だけを持つ（必要なときに UserRepository で取得する）
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TokenPurpose purpose;

    // DB 側は CHAR(64)。Hibernate の型チェック（ddl-auto: validate）を通すため CHAR として扱う
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserToken() {
    }

    public static UserToken issue(Long userId, TokenPurpose purpose, String tokenHash, Instant now,
                                  Instant expiresAt) {
        UserToken token = new UserToken();
        token.userId = userId;
        token.purpose = purpose;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = expiresAt;
        return token;
    }

    /** 未使用で、有効期限内か。expiresAt ちょうどの時刻からは無効。 */
    public boolean isUsableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }
}
