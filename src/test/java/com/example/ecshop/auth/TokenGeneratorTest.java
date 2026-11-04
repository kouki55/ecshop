package com.example.ecshop.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokenGeneratorTest {

    private final TokenGenerator generator = new TokenGenerator();

    @Test
    void トークンはURLに載せられる43文字で毎回異なる() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = generator.generate();
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void ハッシュは64文字の16進数で同じ入力なら同じ値になる() {
        String token = generator.generate();

        assertThat(generator.hash(token)).hasSize(64).matches("[0-9a-f]+").isEqualTo(generator.hash(token));
        assertThat(generator.hash(token)).isNotEqualTo(token).isNotEqualTo(generator.hash(token + "x"));
    }
}
