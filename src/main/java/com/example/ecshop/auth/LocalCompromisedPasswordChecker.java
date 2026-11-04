package com.example.ecshop.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;

/**
 * 漏えい済みパスワードを、ローカルのリストで判定する（外部 API には接続しない）。
 *
 * <p>Spring Security の CompromisedPasswordChecker を実装しているので、
 * 将来 HaveIBeenPwnedRestApiPasswordChecker（HIBP の API 版）などに差し替えられる。
 * 大文字・小文字の違いだけの変形（PasswordPassword など）も拒否するため、小文字にそろえて比較する。
 */
public class LocalCompromisedPasswordChecker implements CompromisedPasswordChecker {

    private final Set<String> compromised;

    public LocalCompromisedPasswordChecker(Collection<String> passwords) {
        Set<String> set = new HashSet<>();
        for (String p : passwords) {
            set.add(normalize(p));
        }
        this.compromised = Set.copyOf(set);
    }

    /** gzip 圧縮された 1 行 1 パスワードのファイルから読み込む。行の前後の空白も、パスワードの一部として残す。 */
    public static LocalCompromisedPasswordChecker fromGzipResource(Resource resource) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(resource.getInputStream()), StandardCharsets.UTF_8))) {
            return new LocalCompromisedPasswordChecker(reader.lines().toList());
        } catch (IOException e) {
            // リストが読めないまま起動すると、漏えいパスワードを受け入れてしまうので起動を止める
            throw new UncheckedIOException("漏えい済みパスワードのリストを読み込めません", e);
        }
    }

    @Override
    public CompromisedPasswordDecision check(String password) {
        return new CompromisedPasswordDecision(password != null && compromised.contains(normalize(password)));
    }

    private static String normalize(String password) {
        // Locale.ROOT を指定し、実行環境の言語設定（トルコ語の i など）で結果が変わらないようにする
        return password.toLowerCase(Locale.ROOT);
    }
}
