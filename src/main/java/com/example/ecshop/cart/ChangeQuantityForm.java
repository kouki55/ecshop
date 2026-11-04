package com.example.ecshop.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** カート画面の数量変更の入力。どの明細かは URL の ID で決め、持ち主はログイン中の会員で確かめる。 */
public class ChangeQuantityForm {

    @NotNull(message = "数量を入力してください")
    @Min(value = 1, message = "数量は1〜" + CartItem.MAX_QUANTITY + "で指定してください")
    @Max(value = CartItem.MAX_QUANTITY, message = "数量は1〜" + CartItem.MAX_QUANTITY + "で指定してください")
    private Integer quantity;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
