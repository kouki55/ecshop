package com.example.ecshop.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * 確認画面の「注文を確定する」の入力。支払い方法以外は確認画面が hidden 項目で埋め込んだ値。
 *
 * <ul>
 *   <li>idempotencyKey：二重送信を見分けるキー。同じキーの注文は 2 件作らない</li>
 *   <li>expectedTotal：確認画面に表示した合計。確定の時点で金額が変わっていたら注文しない
 *       （利用者が見ていない金額で支払わせないため）</li>
 * </ul>
 */
public class PlaceOrderForm {

    @NotNull
    private UUID idempotencyKey;

    @NotNull
    @Positive
    private Long addressId;

    @NotNull
    @PositiveOrZero
    private Long expectedTotal;

    @NotBlank
    @Size(max = 64)
    private String paymentToken;

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(UUID idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }

    public Long getExpectedTotal() {
        return expectedTotal;
    }

    public void setExpectedTotal(Long expectedTotal) {
        this.expectedTotal = expectedTotal;
    }

    public String getPaymentToken() {
        return paymentToken;
    }

    public void setPaymentToken(String paymentToken) {
        this.paymentToken = paymentToken;
    }
}
