package com.example.ecshop.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    void 時刻はDBと同じマイクロ秒単位になっている() {
        assertThat(new ClockConfig().clock().instant().getNano() % 1_000).isZero();
    }
}
