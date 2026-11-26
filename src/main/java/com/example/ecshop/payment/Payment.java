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

    // 返金で SUCCEEDED → REFUNDED に変わるので、更新できるようにしている
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "refund_transaction_id", length = 64)
    private String refundTransactionId;

    @Column(name = "refunded_at")
    private Instant refundedAt;

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

    /** 返金したことを記録する。成功した支払い以外は返金できない。 */
    public void markRefunded(String refundTransactionId, Instant now) {
        if (status != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("成功した支払いではないので返金できません: " + status);
        }
        this.status = PaymentStatus.REFUNDED;
        this.refundTransactionId = refundTransactionId;
        this.refundedAt = now;
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
