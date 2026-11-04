package com.example.ecshop.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 商品詳細画面の「カートに入れる」の入力。 */
public class AddToCartForm {

    @NotNull
    @Positive
    private Long productId;

    @NotNull(message = "数量を入力してください")
    @Min(value = 1, message = "数量は1〜" + CartItem.MAX_QUANTITY + "で指定してください")
    @Max(value = CartItem.MAX_QUANTITY, message = "数量は1〜" + CartItem.MAX_QUANTITY + "で指定してください")
    private Integer quantity;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
