package com.example.ecshop.payment;

/** 支払い 1 回ごとの結果（payments.status）。 */
public enum PaymentStatus {
    SUCCEEDED,
    FAILED,
    /** 支払いに成功したあと、注文のキャンセルで返金した。 */
    REFUNDED
}
