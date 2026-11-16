package com.example.ecshop.product;

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
import java.time.Instant;

/**
 * 商品（products テーブル）。商品は削除せず、published = false（非公開）にする。
 * 価格は税抜の円（long）で持つ。在庫を減らす処理は注文の機能で追加する。
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY：商品を読むたびにカテゴリまで読まない。必要な検索では @EntityGraph で一緒に読む
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    /** 税抜単価（円）。 */
    @Column(nullable = false)
    private long price;

    /** 税率（%）。8 か 10。 */
    @Column(name = "tax_rate", nullable = false)
    private int taxRate;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public static Product create(Category category, String name, String description, long price,
                                 TaxRate taxRate, int stock, boolean published, Instant now) {
        // DB の CHECK 制約と同じ条件を、保存する前にも確かめる
        if (price < 0 || stock < 0) {
            throw new IllegalArgumentException("価格と在庫は 0 以上にしてください");
        }
        Product product = new Product();
        product.category = category;
        product.name = name;
        product.description = description;
        product.price = price;
        product.taxRate = taxRate.percent();
        product.stock = stock;
        product.published = published;
        product.createdAt = now;
        product.updatedAt = now;
        return product;
    }

    /**
     * 注文の確定で在庫を減らす。呼び出す前に、この商品の行を悲観ロックで取得しておくこと
     * （ProductRepository.findAllByIdForUpdate）。ロックなしで減らすと、同時の注文で在庫を売り越すおそれがある。
     */
    public void decreaseStock(int quantity, Instant now) {
        if (quantity <= 0 || quantity > stock) {
            throw new IllegalStateException("在庫が足りません");
        }
        this.stock -= quantity;
        this.updatedAt = now;
    }

    /** 注文のキャンセルで在庫を戻す。 */
    public void increaseStock(int quantity, Instant now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("数量は 1 以上にしてください");
        }
        this.stock = Math.addExact(stock, quantity);
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getPrice() {
        return price;
    }

    public TaxRate getTaxRate() {
        return TaxRate.ofPercent(taxRate);
    }

    public int getStock() {
        return stock;
    }

    public boolean isPublished() {
        return published;
    }
}
