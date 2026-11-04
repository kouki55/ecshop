package com.example.ecshop.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * カート（carts テーブル）。会員 1 人につき 1 つで、最初に商品を入れたときに作る。
 * 中身（cart_items）は CartItem として別に扱う。
 */
@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 持ち主は作成時に決まり、あとから変えられない
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Cart() {
    }

    public static Cart create(long userId, Instant now) {
        Cart cart = new Cart();
        cart.userId = userId;
        cart.createdAt = now;
        cart.updatedAt = now;
        return cart;
    }

    /** 中身が変わったときに更新日時を進める。 */
    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }
}
