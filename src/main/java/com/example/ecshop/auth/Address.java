package com.example.ecshop.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 会員の住所（addresses テーブル）。1 人が複数持て、注文時に配送先として選ぶ。
 * 電話番号は配送の連絡先として住所ごとに持つ。
 */
@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 持ち主は作成時に決まり、あとから変えられない（updatable = false）
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    // DB 側は CHAR(7)。Hibernate の型チェック（ddl-auto: validate）を通すため CHAR として扱う
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "postal_code", nullable = false, length = 7)
    private String postalCode;

    @Column(nullable = false, length = 10)
    private String prefecture;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "address_line1", nullable = false, length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(nullable = false, length = 11)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Address() {
    }

    public static Address create(Long userId, AddressForm form, Instant now) {
        Address address = new Address();
        address.userId = userId;
        address.createdAt = now;
        address.update(form, now);
        return address;
    }

    /** フォームの内容で書き換える。持ち主（userId）は変えない。 */
    public void update(AddressForm form, Instant now) {
        this.recipientName = form.getRecipientName();
        this.postalCode = form.getPostalCode();
        this.prefecture = form.getPrefecture();
        this.city = form.getCity();
        this.addressLine1 = form.getAddressLine1();
        this.addressLine2 = form.getAddressLine2();
        this.phone = form.getPhone();
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getPrefecture() {
        return prefecture;
    }

    public String getCity() {
        return city;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getPhone() {
        return phone;
    }
}
