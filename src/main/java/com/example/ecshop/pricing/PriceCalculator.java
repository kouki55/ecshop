package com.example.ecshop.pricing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * カートと注文の金額計算。金額の計算はすべてこのクラスにまとめる。
 *
 * <ul>
 *   <li>消費税：税率ごとに税抜の合計を出し、それぞれに 1 回だけ端数を切り捨てる。
 *       商品ごとに切り捨ててから足し合わせない（国税庁 インボイスQ&A 問57 の考え方）</li>
 *   <li>送料：全国一律 500 円。商品の税込合計（送料を含めない）が 5,000 円以上なら無料</li>
 * </ul>
 *
 * <p>金額はすべて円の整数（long）で扱い、桁あふれは例外にする。
 */
public final class PriceCalculator {

    public static final long SHIPPING_FEE = 500;
    public static final long FREE_SHIPPING_THRESHOLD = 5_000;

    /** 計算する明細 1 行。unitPrice は税抜単価。 */
    public record Line(long unitPrice, TaxRate taxRate, int quantity) {
        public Line {
            if (unitPrice < 0 || quantity <= 0) {
                throw new IllegalArgumentException("単価は 0 以上、数量は 1 以上にしてください");
            }
        }
    }

    /** 税率ごとの内訳。subtotal は税抜の合計、tax はそれに対する消費税（切り捨て済み）。 */
    public record TaxBreakdown(TaxRate taxRate, long subtotal, long tax) {
    }

    /**
     * 計算結果。orders テーブルの列（subtotal / tax_amount / shipping_fee / total_amount）と対応する。
     *
     * @param subtotal    税抜の合計
     * @param taxAmount   消費税の合計（税率ごとに切り捨てた額の和）
     * @param shippingFee 送料
     * @param total       支払う合計（subtotal + taxAmount + shippingFee）
     * @param breakdowns  税率ごとの内訳（その税率の明細がある分だけ。税率の低い順）
     */
    public record Result(long subtotal, long taxAmount, long shippingFee, long total, List<TaxBreakdown> breakdowns) {
        public Result {
            breakdowns = List.copyOf(breakdowns);
        }

        /** 商品の税込合計（送料を含めない）。送料無料の判定に使う。 */
        public long itemsTotalIncludingTax() {
            return subtotal + taxAmount;
        }
    }

    private PriceCalculator() {
    }

    public static Result calculate(List<Line> lines) {
        if (lines.isEmpty()) {
            // 何も買わないなら送料もかからない
            return new Result(0, 0, 0, 0, List.of());
        }

        // 1. 税率ごとに税抜の合計を出す（EnumMap は enum の宣言順＝税率の低い順に並ぶ）
        Map<TaxRate, Long> subtotalByRate = new EnumMap<>(TaxRate.class);
        for (Line line : lines) {
            long lineTotal = Math.multiplyExact(line.unitPrice(), (long) line.quantity());
            subtotalByRate.merge(line.taxRate(), lineTotal, Math::addExact);
        }

        // 2. 税率ごとの合計に対して、1 回だけ消費税を計算して切り捨てる
        List<TaxBreakdown> breakdowns = new ArrayList<>();
        long subtotal = 0;
        long taxAmount = 0;
        for (Map.Entry<TaxRate, Long> entry : subtotalByRate.entrySet()) {
            long rateSubtotal = entry.getValue();
            long tax = Math.multiplyExact(rateSubtotal, (long) entry.getKey().percent()) / 100;
            breakdowns.add(new TaxBreakdown(entry.getKey(), rateSubtotal, tax));
            subtotal = Math.addExact(subtotal, rateSubtotal);
            taxAmount = Math.addExact(taxAmount, tax);
        }

        // 3. 送料は、商品の税込合計で判定する
        long itemsTotal = Math.addExact(subtotal, taxAmount);
        long shippingFee = itemsTotal >= FREE_SHIPPING_THRESHOLD ? 0 : SHIPPING_FEE;
        long total = Math.addExact(itemsTotal, shippingFee);
        return new Result(subtotal, taxAmount, shippingFee, total, breakdowns);
    }
}
