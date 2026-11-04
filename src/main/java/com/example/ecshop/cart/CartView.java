package com.example.ecshop.cart;

import com.example.ecshop.pricing.PriceCalculator;
import java.util.List;

/**
 * カート画面に渡す情報。price は購入できる明細（status が OK）だけで計算した金額。
 */
public record CartView(List<Line> lines, PriceCalculator.Result price) {

    public CartView {
        lines = List.copyOf(lines);
    }

    /**
     * 明細 1 行。unitPrice は税抜単価、subtotal は税抜単価 × 数量。
     * 税込単価は目安として表示するもので、合計の消費税は price の方で税率ごとに計算する。
     */
    public record Line(Long itemId, Long productId, String productName, long unitPrice, long unitPriceIncludingTax,
                       int taxRatePercent, int quantity, long subtotal, LineStatus status) {
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public boolean hasUnavailableLines() {
        return lines.stream().anyMatch(line -> !line.status().purchasable());
    }
}
