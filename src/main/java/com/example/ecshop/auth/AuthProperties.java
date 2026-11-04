package com.example.ecshop.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * 認証まわりの設定（application.yml の app.auth）。
 *
 * @param baseUrl                 メール内のリンクの先頭部分（例：http://localhost:8080）
 * @param mailFrom                送信元メールアドレス
 * @param verificationTokenTtl    メール確認リンクの有効期限
 * @param passwordResetTokenTtl   パスワード再設定リンクの有効期限
 * @param compromisedPasswordList 漏えい済みパスワードのリスト（gzip 圧縮した 1 行 1 パスワードのファイル）
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(String baseUrl, String mailFrom, Duration verificationTokenTtl,
                             Duration passwordResetTokenTtl, Resource compromisedPasswordList) {
}
