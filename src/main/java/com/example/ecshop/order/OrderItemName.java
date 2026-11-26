package com.example.ecshop.order;

/** 注文履歴の一覧のために、明細から取り出す値（OrderRepository の JPQL の select new で作る）。 */
public record OrderItemName(Long orderId, String productName) {
}
