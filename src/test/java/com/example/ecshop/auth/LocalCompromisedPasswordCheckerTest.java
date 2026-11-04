package com.example.ecshop.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecshop.TestUsers;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class LocalCompromisedPasswordCheckerTest {

    @Test
    void 大文字小文字を区別せずに判定する() {
        LocalCompromisedPasswordChecker checker = new LocalCompromisedPasswordChecker(List.of("ILoveMyCarVeryMuch"));

        assertThat(checker.check("ilovemycarverymuch").isCompromised()).isTrue();
        assertThat(checker.check("ILOVEMYCARVERYMUCH").isCompromised()).isTrue();
        assertThat(checker.check("ilovemycarverymuch!").isCompromised()).isFalse();
        assertThat(checker.check(null).isCompromised()).isFalse();
    }

    @Test
    void 同梱のリストを読み込める() {
        LocalCompromisedPasswordChecker checker = LocalCompromisedPasswordChecker.fromGzipResource(
                new ClassPathResource("security/compromised-passwords.txt.gz"));

        // 実際の漏えいリストに含まれる 15 文字以上のパスワード
        assertThat(checker.check("passwordpassword").isCompromised()).isTrue();
        assertThat(checker.check("123456789012345").isCompromised()).isTrue();
        assertThat(checker.check("QwertyuiopAsdfgh").isCompromised()).isTrue();
        // テストで使うパスワードは含まれていない
        assertThat(checker.check(TestUsers.PASSWORD).isCompromised()).isFalse();
    }
}
