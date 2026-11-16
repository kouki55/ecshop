package com.example.ecshop.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 配送先の選択（確認画面へ進むときの入力）。 */
public class ConfirmForm {

    @NotNull
    @Positive
    private Long addressId;

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }
}
