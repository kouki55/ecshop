package com.example.ecshop.pricing;

/**
 * 消費税率。DB の products.tax_rate（CHECK で 8 か 10 だけ）と対応する。
 *
 * <p>ここで求めるのは画面に表示する「税込単価」だけ。カートや注文の消費税は、税率ごとの合計に対して
 * 1 回だけ端数を切り捨てる（{@link PriceCalculator} が担当する）ため、税込単価を足した額とは
 * 数円ずれることがある。
 */
public enum TaxRate {
    /** 軽減税率（飲食料品など）。 */
    REDUCED(8),
    /** 標準税率。 */
    STANDARD(10);

    private final int percent;

    TaxRate(int percent) {
        this.percent = percent;
    }

    public int percent() {
        return percent;
    }

    public static TaxRate ofPercent(int percent) {
        for (TaxRate rate : values()) {
            if (rate.percent == percent) {
                return rate;
            }
        }
        throw new IllegalArgumentException("対応していない税率です: " + percent);
    }

    /**
     * 税抜単価から税込単価を求める（円未満は切り捨て）。
     * 金額は 0 以上なので、整数の割り算（小数点以下を捨てる）がそのまま切り捨てになる。
     * double を使うと 0.1 などを正確に表せず誤差が出るため、整数だけで計算する。
     */
    public long includeTax(long priceExcludingTax) {
        if (priceExcludingTax < 0) {
            throw new IllegalArgumentException("金額が負の数です");
        }
        // 桁あふれ（long の上限超え）は黙って間違った値にせず、例外にする
        return Math.multiplyExact(priceExcludingTax, 100 + percent) / 100;
    }
}
