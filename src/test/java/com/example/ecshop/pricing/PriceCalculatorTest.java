package com.example.ecshop.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ecshop.pricing.PriceCalculator.Line;
import com.example.ecshop.pricing.PriceCalculator.Result;
import com.example.ecshop.pricing.PriceCalculator.TaxBreakdown;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceCalculatorTest {

    // ---------------------------------------------------------------- 送料の境界

    @Test
    void 商品の税込合計が4999円なら送料がかかる() {
        // 4,545 × 10% = 454.5 → 454 円。税込合計 4,999 円
        Result result = PriceCalculator.calculate(List.of(new Line(4545, TaxRate.STANDARD, 1)));

        assertThat(result.itemsTotalIncludingTax()).isEqualTo(4999);
        assertThat(result.shippingFee()).isEqualTo(PriceCalculator.SHIPPING_FEE);
        assertThat(result.total()).isEqualTo(4999 + 500);
    }

    @Test
    void 商品の税込合計が5000円なら送料は無料() {
        // 4,546 × 10% = 454.6 → 454 円。税込合計 5,000 円
        Result result = PriceCalculator.calculate(List.of(new Line(4546, TaxRate.STANDARD, 1)));

        assertThat(result.itemsTotalIncludingTax()).isEqualTo(5000);
        assertThat(result.shippingFee()).isZero();
        assertThat(result.total()).isEqualTo(5000);
    }

    @Test
    void 送料の判定は税抜ではなく税込の合計で行う() {
        // 税抜 4,630 円は 5,000 円未満だが、8% の税込は 4,630 + 370 = 5,000 円なので無料
        Result result = PriceCalculator.calculate(List.of(new Line(4630, TaxRate.REDUCED, 1)));

        assertThat(result.subtotal()).isLessThan(PriceCalculator.FREE_SHIPPING_THRESHOLD);
        assertThat(result.itemsTotalIncludingTax()).isEqualTo(5000);
        assertThat(result.shippingFee()).isZero();
    }

    // ---------------------------------------------------------------- 消費税の端数

    @Test
    void 消費税は商品ごとではなく税率ごとの合計に対して1回だけ切り捨てる() {
        // 999 円 × 2 個 = 1,998 円。1,998 × 10% = 199.8 → 199 円
        // （商品ごとに切り捨てると 99 + 99 = 198 円になるが、それは誤り）
        Result result = PriceCalculator.calculate(List.of(new Line(999, TaxRate.STANDARD, 2)));

        assertThat(result.subtotal()).isEqualTo(1998);
        assertThat(result.taxAmount()).isEqualTo(199);
    }

    @Test
    void 別々の明細でも同じ税率なら合計してから切り捨てる() {
        // 1,005 + 1,005 = 2,010 円。2,010 × 10% = 201 円（明細ごとだと 100 + 100 = 200 円）
        Result result = PriceCalculator.calculate(List.of(
                new Line(1005, TaxRate.STANDARD, 1),
                new Line(1005, TaxRate.STANDARD, 1)));

        assertThat(result.taxAmount()).isEqualTo(201);
    }

    @Test
    void 税率が混ざると税率ごとに別々に切り捨てる() {
        // 10%：999 × 10% = 99.9 → 99 円
        // 8%：999 × 8% = 79.92 → 79 円
        Result result = PriceCalculator.calculate(List.of(
                new Line(999, TaxRate.STANDARD, 1),
                new Line(999, TaxRate.REDUCED, 1)));

        assertThat(result.breakdowns()).containsExactly(
                new TaxBreakdown(TaxRate.REDUCED, 999, 79),
                new TaxBreakdown(TaxRate.STANDARD, 999, 99));
        assertThat(result.subtotal()).isEqualTo(1998);
        assertThat(result.taxAmount()).isEqualTo(178);
        assertThat(result.shippingFee()).isEqualTo(500);
        assertThat(result.total()).isEqualTo(1998 + 178 + 500);
    }

    @Test
    void 合計は税抜合計と消費税と送料の和になる() {
        Result result = PriceCalculator.calculate(List.of(
                new Line(1280, TaxRate.STANDARD, 3),
                new Line(1980, TaxRate.REDUCED, 1)));

        assertThat(result.total()).isEqualTo(result.subtotal() + result.taxAmount() + result.shippingFee());
    }

    // ---------------------------------------------------------------- 特別な場合

    @Test
    void 何もなければ合計も送料も0円() {
        Result result = PriceCalculator.calculate(List.of());

        assertThat(result.total()).isZero();
        assertThat(result.shippingFee()).isZero();
        assertThat(result.breakdowns()).isEmpty();
    }

    @Test
    void 零円の商品だけなら送料がかかる() {
        Result result = PriceCalculator.calculate(List.of(new Line(0, TaxRate.STANDARD, 1)));

        assertThat(result.taxAmount()).isZero();
        assertThat(result.total()).isEqualTo(500);
    }

    @Test
    void 桁あふれする金額は黙って間違えず例外にする() {
        assertThatThrownBy(() -> PriceCalculator.calculate(List.of(new Line(Long.MAX_VALUE / 2, TaxRate.STANDARD, 3))))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void 負の単価や0個以下の数量は受け付けない() {
        assertThatThrownBy(() -> new Line(-1, TaxRate.STANDARD, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Line(100, TaxRate.STANDARD, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
