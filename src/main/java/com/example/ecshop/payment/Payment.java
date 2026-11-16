package com.example.ecshop.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 支払いの記録（payments テーブル）。失敗も含めて 1 回の支払いごとに 1 行残す。
 * カード番号や支払いトークンは保存しない。取引 ID・状態・金額だけを持つ。
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "transaction_id", nullable = false, updatable = false, length = 64)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
    }

    public static Payment record(long orderId, PaymentGateway.ChargeResult result, long amount, Instant now) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.transactionId = result.transactionId();
        payment.status = result.succeeded() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED;
        payment.amount = amount;
        payment.createdAt = now;
        return payment;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public long getAmount() {
        return amount;
    }
}
