package com.example.ecshop.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * パスワード規則の単体テスト。漏えいリストは小さなテスト用のものを使う。
 */
class PasswordPolicyTest {

    private static final String EMAIL = "taro.yamada.example@example.com";

    private final PasswordPolicy policy = new PasswordPolicy(
            new LocalCompromisedPasswordChecker(List.of("passwordpassword123")));

    @Test
    void 文字数14のパスワードは拒否される() {
        assertThat(policy.validate("a".repeat(14), EMAIL)).hasValue("パスワードは15文字以上で入力してください");
    }

    @Test
    void 文字数15のパスワードは通る() {
        assertThat(policy.validate("abcdefghijklmno", EMAIL)).isEmpty();
    }

    @Test
    void 文字数64のパスワードは通り65は拒否される() {
        assertThat(policy.validate("a".repeat(64), EMAIL)).isEmpty();
        assertThat(policy.validate("a".repeat(65), EMAIL)).hasValue("パスワードは64文字以内で入力してください");
    }

    @Test
    void 文字の種類の組み合わせは強制しない() {
        // 小文字だけ・数字だけでも、長さを満たせば通る
        assertThat(policy.validate("onlylowercaseletters", EMAIL)).isEmpty();
        assertThat(policy.validate("820461937502846", EMAIL)).isEmpty();
    }

    @Test
    void UTF8で72バイトまでは通り73バイト以上は拒否される() {
        // 日本語は 1 文字 3 バイト。24 文字 = 72 バイト、25 文字 = 75 バイト
        assertThat(policy.validate("あ".repeat(24), EMAIL)).isEmpty();
        assertThat(policy.validate("あ".repeat(25), EMAIL)).get().asString().contains("72バイト");
        // 英数字と混ぜた場合：58 + 5×3 = 73 バイト（文字数は 63 なので文字数の規則は満たしている）
        assertThat(policy.validate("a".repeat(58) + "あ".repeat(5), EMAIL)).get().asString().contains("72バイト");
        assertThat(policy.validate("a".repeat(57) + "あ".repeat(5), EMAIL)).isEmpty();
    }

    @Test
    void 絵文字などは1文字として数える() {
        // 🚗 は Java の String では 2 文字分（サロゲートペア）だが、1 文字として数える
        assertThat(policy.validate("🚗".repeat(14), EMAIL)).hasValue("パスワードは15文字以上で入力してください");
        assertThat(policy.validate("🚗".repeat(15), EMAIL)).isEmpty();
    }

    @Test
    void 前後の空白は削らずに文字数に含める() {
        // 13 文字 + 前後の空白 2 文字 = 15 文字
        assertThat(policy.validate(" abcdefghijklm ", EMAIL)).isEmpty();
    }

    @Test
    void 空のパスワードは拒否される() {
        assertThat(policy.validate("", EMAIL)).hasValue("パスワードを入力してください");
        assertThat(policy.validate(null, EMAIL)).hasValue("パスワードを入力してください");
    }

    @Test
    void 漏えいリストにあるパスワードは大文字小文字が違っても拒否される() {
        String message = "このパスワードは過去に漏えいしたパスワードの一覧に含まれているため使えません";
        assertThat(policy.validate("passwordpassword123", EMAIL)).hasValue(message);
        assertThat(policy.validate("PasswordPassword123", EMAIL)).hasValue(message);
    }

    @Test
    void メールアドレスやその前半と同じパスワードは拒否される() {
        String message = "メールアドレスと同じパスワードは使えません";
        assertThat(policy.validate(EMAIL, EMAIL)).hasValue(message);
        assertThat(policy.validate("Taro.Yamada.Example", EMAIL)).hasValue(message);
    }
}
