package com.example.ecshop.cart;

import com.example.ecshop.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * カートの明細（cart_items テーブル）。同じ商品は 1 行にまとめ、数量で表す。
 * 価格は持たない（表示のたびに商品の今の価格で計算する）。
 */
@Entity
@Table(name = "cart_items")
public class CartItem {

    /** 1 行の数量の上限。 */
    public static final int MAX_QUANTITY = 99;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // どのカートの明細かは作成時に決まり、あとから変えられない
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false, updatable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartItem() {
    }

    public static CartItem create(Cart cart, Product product, int quantity, Instant now) {
        CartItem item = new CartItem();
        item.cart = cart;
        item.product = product;
        item.createdAt = now;
        item.changeQuantity(quantity, now);
        return item;
    }

    /** 数量を変える。1〜99 の範囲外は受け付けない（DB の CHECK (quantity > 0) より狭い範囲をここで守る）。 */
    public void changeQuantity(int newQuantity, Instant now) {
        if (newQuantity < 1 || newQuantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("数量は 1〜" + MAX_QUANTITY + " にしてください");
        }
        this.quantity = newQuantity;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Cart getCart() {
        return cart;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }
}
