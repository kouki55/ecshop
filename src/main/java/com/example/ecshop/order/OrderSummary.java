package com.example.ecshop.order;

import java.util.List;

/**
 * 注文履歴の一覧の 1 行。一覧では明細の全項目は要らないので、先頭の商品名と商品の種類数だけを持つ。
 *
 * @param firstProductName 先頭の明細の商品名
 * @param itemCount        明細の行数（商品の種類数）
 */
public record OrderSummary(long id, String orderedAt, OrderStatus status, long totalAmount,
                           String firstProductName, int itemCount) {

    static OrderSummary of(Order order, List<String> productNames) {
        return new OrderSummary(order.getId(), OrderView.format(order.getOrderedAt()), order.getStatus(),
                order.getTotalAmount(), productNames.isEmpty() ? "" : productNames.getFirst(), productNames.size());
    }
}
