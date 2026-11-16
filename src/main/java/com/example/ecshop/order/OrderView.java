package com.example.ecshop.order;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 画面とメールに渡す注文の情報。open-in-view を切っているので、画面の描画中に明細を遅延読み込みできない。
 * そのためトランザクションの中で、必要な値をすべてこの record にコピーしてから渡す。
 */
public record OrderView(long id, OrderStatus status, long subtotal, long taxAmount, long shippingFee,
                        long totalAmount, List<Line> lines, String recipientName, String postalCode,
                        String address, String phone, String orderedAt, String paymentDeadline,
                        String deliveryLabel) {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy年M月d日(E) H:mm", Locale.JAPANESE);

    public OrderView {
        lines = List.copyOf(lines);
    }

    /** 明細 1 行。unitPrice は税抜単価、subtotal は税抜単価 × 数量。 */
    public record Line(String productName, long unitPrice, int taxRatePercent, int quantity, long subtotal) {
    }

    static OrderView of(Order order, Duration paymentTimeout) {
        List<Line> lines = order.getItems().stream()
                .map(item -> new Line(item.getProductName(), item.getUnitPrice(), item.getTaxRate().percent(),
                        item.getQuantity(), Math.multiplyExact(item.getUnitPrice(), (long) item.getQuantity())))
                .toList();
        String address = order.getShipPrefecture() + order.getShipCity() + order.getShipAddressLine1()
                + (order.getShipAddressLine2() == null ? "" : " " + order.getShipAddressLine2());
        return new OrderView(order.getId(), order.getStatus(), order.getSubtotal(), order.getTaxAmount(),
                order.getShippingFee(), order.getTotalAmount(), lines, order.getShipRecipientName(),
                order.getShipPostalCode(), address, order.getShipPhone(),
                format(order.getOrderedAt()), format(order.getOrderedAt().plus(paymentTimeout)),
                DeliveryEstimate.from(order.getOrderedAt()).label());
    }

    /** 日時は日本時間で表示する。 */
    static String format(Instant instant) {
        return instant.atZone(DeliveryEstimate.JAPAN).format(DATE_TIME);
    }

    @Override
    public String toString() {
        // 住所・電話番号をログに出さないよう、自動の toString（全項目を出す）を使わない
        return "OrderView[id=" + id + ", status=" + status + "]";
    }
}
