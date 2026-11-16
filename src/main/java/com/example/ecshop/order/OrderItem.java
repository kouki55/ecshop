package com.example.ecshop.order;

import com.example.ecshop.pricing.TaxRate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 注文の明細（order_items テーブル）。商品名・単価・税率は注文時点の値をコピーして持つので、
 * あとで商品を変更しても過去の注文の内容は変わらない。作ったあとは変更しない（setter なし）。
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    // 商品のエンティティとは結び付けず、ID だけを持つ（注文の明細から商品を書き換えてしまわないように）
    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, updatable = false, length = 200)
    private String productName;

    /** 注文時点の税抜単価（円）。 */
    @Column(name = "unit_price", nullable = false, updatable = false)
    private long unitPrice;

    @Column(name = "tax_rate", nullable = false, updatable = false)
    private int taxRate;

    @Column(nullable = false, updatable = false)
    private int quantity;

    protected OrderItem() {
    }

    static OrderItem create(Order order, long productId, String productName, long unitPrice, TaxRate taxRate,
                            int quantity) {
        OrderItem item = new OrderItem();
        item.order = order;
        item.productId = productId;
        item.productName = productName;
        item.unitPrice = unitPrice;
        item.taxRate = taxRate.percent();
        item.quantity = quantity;
        return item;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public TaxRate getTaxRate() {
        return TaxRate.ofPercent(taxRate);
    }

    public int getQuantity() {
        return quantity;
    }
}
