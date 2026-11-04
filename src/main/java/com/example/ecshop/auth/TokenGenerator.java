package com.example.ecshop.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * メール確認などに使うトークンの生成とハッシュ化。
 * <ul>
 *   <li>生成：SecureRandom で 32 バイト（256 ビット）作り、URL に載せられる Base64URL（43 文字）にする</li>
 *   <li>保存：DB には SHA-256 の 16 進文字列（64 文字）だけを保存する。DB が漏れてもリンクは作れない</li>
 * </ul>
 * トークン自体が十分長いランダム値なので、パスワードと違い bcrypt のような遅いハッシュは不要。
 */
@Component
public class TokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 はすべての Java 実行環境で必ず使えるので、ここには来ない
            throw new IllegalStateException(e);
        }
    }
}
