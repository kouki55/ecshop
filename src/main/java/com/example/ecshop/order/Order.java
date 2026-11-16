package com.example.ecshop.order;

import com.example.ecshop.auth.Address;
import com.example.ecshop.pricing.PriceCalculator;
import com.example.ecshop.product.Product;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 注文（orders テーブル）。金額と配送先は注文時点の値をコピーして持つ。
 *
 * <p>エンティティ名を CustomerOrder にしているのは、ORDER が SQL / JPQL の予約語（ORDER BY）で、
 * そのままだとクエリの中で使えないため。テーブル名は orders のまま。
 */
@Entity(name = "CustomerOrder")
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** 二重送信を見分けるためのキー（確認画面を出すときに作る）。 */
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private long subtotal;

    @Column(name = "tax_amount", nullable = false, updatable = false)
    private long taxAmount;

    @Column(name = "shipping_fee", nullable = false, updatable = false)
    private long shippingFee;

    @Column(name = "total_amount", nullable = false, updatable = false)
    private long totalAmount;

    @Column(name = "ship_recipient_name", nullable = false, updatable = false, length = 100)
    private String shipRecipientName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ship_postal_code", nullable = false, updatable = false, length = 7)
    private String shipPostalCode;

    @Column(name = "ship_prefecture", nullable = false, updatable = false, length = 10)
    private String shipPrefecture;

    @Column(name = "ship_city", nullable = false, updatable = false, length = 100)
    private String shipCity;

    @Column(name = "ship_address_line1", nullable = false, updatable = false, length = 200)
    private String shipAddressLine1;

    @Column(name = "ship_address_line2", updatable = false, length = 200)
    private String shipAddressLine2;

    @Column(name = "ship_phone", nullable = false, updatable = false, length = 11)
    private String shipPhone;

    @Column(name = "ordered_at", nullable = false, updatable = false)
    private Instant orderedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 注文を保存すると明細も一緒に保存される（cascade）
    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    /** 受付の状態の注文を作る。金額と配送先はこの時点の値をコピーする。 */
    public static Order create(long userId, UUID idempotencyKey, Address address, PriceCalculator.Result price,
                               Instant now) {
        Order order = new Order();
        order.userId = userId;
        order.idempotencyKey = idempotencyKey;
        order.status = OrderStatus.RECEIVED;
        order.subtotal = price.subtotal();
        order.taxAmount = price.taxAmount();
        order.shippingFee = price.shippingFee();
        order.totalAmount = price.total();
        order.shipRecipientName = address.getRecipientName();
        order.shipPostalCode = address.getPostalCode();
        order.shipPrefecture = address.getPrefecture();
        order.shipCity = address.getCity();
        order.shipAddressLine1 = address.getAddressLine1();
        order.shipAddressLine2 = address.getAddressLine2();
        order.shipPhone = address.getPhone();
        order.orderedAt = now;
        order.updatedAt = now;
        return order;
    }

    /** 明細を加える。商品名・単価・税率はこの時点の商品の値をコピーする。 */
    public void addItem(Product product, int quantity) {
        items.add(OrderItem.create(this, product.getId(), product.getName(), product.getPrice(),
                product.getTaxRate(), quantity));
    }

    /** 状態を変える。決められた順番以外の変化（例：キャンセル済みを支払済にする）は受け付けない。 */
    public void changeStatus(OrderStatus next, Instant now) {
        if (!status.canChangeTo(next)) {
            throw new IllegalStateException("注文の状態を " + status + " から " + next + " には変えられません");
        }
        this.status = next;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getSubtotal() {
        return subtotal;
    }

    public long getTaxAmount() {
        return taxAmount;
    }

    public long getShippingFee() {
        return shippingFee;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public String getShipRecipientName() {
        return shipRecipientName;
    }

    public String getShipPostalCode() {
        return shipPostalCode;
    }

    public String getShipPrefecture() {
        return shipPrefecture;
    }

    public String getShipCity() {
        return shipCity;
    }

    public String getShipAddressLine1() {
        return shipAddressLine1;
    }

    public String getShipAddressLine2() {
        return shipAddressLine2;
    }

    public String getShipPhone() {
        return shipPhone;
    }

    public Instant getOrderedAt() {
        return orderedAt;
    }

    /** 明細（変更できないリストで返す）。 */
    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }
}
