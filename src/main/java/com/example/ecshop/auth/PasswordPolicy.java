package com.example.ecshop.auth;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.stereotype.Component;

/**
 * パスワードの規則（NIST SP 800-63B-4 のパスワードだけでログインする場合の基準に合わせる）。
 * <ul>
 *   <li>15 文字以上 64 文字以内（文字数は Unicode のコードポイントで数える）。文字種の組み合わせは強制しない</li>
 *   <li>UTF-8 で 72 バイト以内（bcrypt が扱える上限。日本語は 1 文字 3 バイト）</li>
 *   <li>メールアドレス（または @ より前の部分）と同じものは不可</li>
 *   <li>漏えい済みの既知パスワードは不可</li>
 * </ul>
 * 前後の空白も削らずにパスワードの一部として扱う。
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 15;
    public static final int MAX_LENGTH = 64;
    public static final int MAX_BYTES = 72;

    private final CompromisedPasswordChecker compromisedPasswordChecker;

    public PasswordPolicy(CompromisedPasswordChecker compromisedPasswordChecker) {
        this.compromisedPasswordChecker = compromisedPasswordChecker;
    }

    /** 規則に合わなければ、利用者に見せるメッセージを返す。合っていれば空。 */
    public Optional<String> validate(String password, String email) {
        if (password == null || password.isEmpty()) {
            return Optional.of("パスワードを入力してください");
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH) {
            return Optional.of("パスワードは" + MIN_LENGTH + "文字以上で入力してください");
        }
        if (length > MAX_LENGTH) {
            return Optional.of("パスワードは" + MAX_LENGTH + "文字以内で入力してください");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return Optional.of("パスワードが長すぎます。日本語などの全角文字は1文字を3バイトとして、合計"
                    + MAX_BYTES + "バイト以内にしてください");
        }
        if (matchesEmail(password, email)) {
            return Optional.of("メールアドレスと同じパスワードは使えません");
        }
        if (compromisedPasswordChecker.check(password).isCompromised()) {
            return Optional.of("このパスワードは過去に漏えいしたパスワードの一覧に含まれているため使えません");
        }
        return Optional.empty();
    }

    private static boolean matchesEmail(String password, String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String lowerPassword = asciiLowerCase(password);
        String lowerEmail = asciiLowerCase(email.strip());
        int at = lowerEmail.indexOf('@');
        String localPart = at > 0 ? lowerEmail.substring(0, at) : lowerEmail;
        return lowerPassword.equals(lowerEmail) || lowerPassword.equals(localPart);
    }

    /**
     * 英字の A〜Z だけを小文字にする。toLowerCase や equalsIgnoreCase は Unicode の変換規則
     * （トルコ語の İ など、変換で別の文字列になったり別の文字と一致したりするもの）に左右されるので使わない。
     * メールアドレスの大文字・小文字の違いを吸収したいだけなので、英字だけで十分。
     */
    private static String asciiLowerCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return sb.toString();
    }
}
