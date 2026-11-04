package com.example.ecshop.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TaxRateTest {

    @Test
    void 割り切れる金額はそのまま税込にする() {
        assertThat(TaxRate.STANDARD.includeTax(1000)).isEqualTo(1100);
        assertThat(TaxRate.REDUCED.includeTax(1000)).isEqualTo(1080);
    }

    @Test
    void 円未満の端数は切り捨てる() {
        // 999 × 1.10 = 1,098.9
        assertThat(TaxRate.STANDARD.includeTax(999)).isEqualTo(1098);
        // 999 × 1.08 = 1,078.92
        assertThat(TaxRate.REDUCED.includeTax(999)).isEqualTo(1078);
        // 1 × 1.10 = 1.1
        assertThat(TaxRate.STANDARD.includeTax(1)).isEqualTo(1);
        // 9 × 1.10 = 9.9（四捨五入なら 10 になるが、切り捨てなので 9）
        assertThat(TaxRate.STANDARD.includeTax(9)).isEqualTo(9);
    }

    @Test
    void 零円は零円() {
        assertThat(TaxRate.STANDARD.includeTax(0)).isZero();
        assertThat(TaxRate.REDUCED.includeTax(0)).isZero();
    }

    @Test
    void 負の金額は受け付けない() {
        assertThatThrownBy(() -> TaxRate.STANDARD.includeTax(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 桁あふれする金額は黙って間違えず例外にする() {
        assertThatThrownBy(() -> TaxRate.STANDARD.includeTax(Long.MAX_VALUE / 100))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void 税率の数値から対応する税率を得る() {
        assertThat(TaxRate.ofPercent(8)).isEqualTo(TaxRate.REDUCED);
        assertThat(TaxRate.ofPercent(10)).isEqualTo(TaxRate.STANDARD);
        assertThatThrownBy(() -> TaxRate.ofPercent(5)).isInstanceOf(IllegalArgumentException.class);
    }
}
