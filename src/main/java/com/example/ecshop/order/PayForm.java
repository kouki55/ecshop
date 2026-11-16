package com.example.ecshop.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 支払いの再試行の入力。 */
public class PayForm {

    @NotBlank
    @Size(max = 64)
    private String paymentToken;

    public String getPaymentToken() {
        return paymentToken;
    }

    public void setPaymentToken(String paymentToken) {
        this.paymentToken = paymentToken;
    }
}
