package com.example.ecshop.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ecshop.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V1 マイグレーションが PostgreSQL に適用でき、データ設計ルールが DB の制約として効いていることを確認する。
 * 各テストは終了時にロールバックされるので、テスト同士でデータは干渉しない。
 */
@IntegrationTest
@Transactional
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long userId;
    private long productId;

    @BeforeEach
    void setUp() {
        userId = insertUser("member@example.com");
        long categoryId = jdbc.queryForObject(
                "INSERT INTO categories (name) VALUES ('エンジンオイル') RETURNING id", Long.class);
        productId = jdbc.queryForObject("""
                INSERT INTO products (category_id, name, price, tax_rate, stock)
                VALUES (?, '5W-30 4L', 3000, 10, 5) RETURNING id
                """, Long.class, categoryId);
    }

    @Test
    void すべてのテーブルが作られている() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                """, String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "users", "user_tokens", "addresses", "carts", "cart_items",
                "products", "categories", "orders", "order_items", "payments");
    }

    @Test
    void メールアドレスは大文字小文字を区別せず重複できない() {
        assertThatThrownBy(() -> insertUser("MEMBER@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 会員の状態は決められた値しか入らない() {
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET status = 'LOCKED' WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 在庫はマイナスにできない() {
        assertThatThrownBy(() -> jdbc.update("UPDATE products SET stock = -1 WHERE id = ?", productId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 同じ冪等キーの注文は2件作れない() {
        UUID key = UUID.randomUUID();
        insertOrder(userId, key, 3000, 300, 500, 3800);

        assertThatThrownBy(() -> insertOrder(userId, key, 3000, 300, 500, 3800))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 存在しない会員の注文は作れない() {
        assertThatThrownBy(() -> insertOrder(999_999L, UUID.randomUUID(), 3000, 300, 500, 3800))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 合計金額が内訳と一致しない注文は作れない() {
        assertThatThrownBy(() -> insertOrder(userId, UUID.randomUUID(), 3000, 300, 500, 9999))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 注文ステータスは決められた値しか入らない() {
        long orderId = insertOrder(userId, UUID.randomUUID(), 3000, 300, 500, 3800);

        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET status = 'REFUNDED' WHERE id = ?", orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 一つの注文に成功した決済は1件だけ() {
        long orderId = insertOrder(userId, UUID.randomUUID(), 3000, 300, 500, 3800);
        insertPayment(orderId, "tx-1", "FAILED");
        insertPayment(orderId, "tx-2", "SUCCEEDED");

        assertThatThrownBy(() -> insertPayment(orderId, "tx-3", "SUCCEEDED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void カートに同じ商品の行は2つ作れない() {
        long cartId = jdbc.queryForObject(
                "INSERT INTO carts (user_id) VALUES (?) RETURNING id", Long.class, userId);
        jdbc.update("INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (?, ?, 1)", cartId, productId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (?, ?, 2)", cartId, productId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void トークンの有効期限は作成日時より後でなければならない() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO user_tokens (user_id, purpose, token_hash, expires_at)
                VALUES (?, 'PASSWORD_RESET', repeat('a', 64), now() - interval '1 minute')
                """, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertUser(String email) {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, name) VALUES (?, 'dummy-hash', 'テスト会員')
                RETURNING id
                """, Long.class, email);
    }

    private long insertOrder(long ownerId, UUID key, long subtotal, long tax, long shipping, long total) {
        return jdbc.queryForObject("""
                INSERT INTO orders (user_id, idempotency_key, subtotal, tax_amount, shipping_fee, total_amount,
                                    ship_recipient_name, ship_postal_code, ship_prefecture, ship_city,
                                    ship_address_line1, ship_phone)
                VALUES (?, ?, ?, ?, ?, ?, '山田太郎', '1000001', '東京都', '千代田区', '千代田1-1', '0312345678')
                RETURNING id
                """, Long.class, ownerId, key, subtotal, tax, shipping, total);
    }

    private void insertPayment(long orderId, String transactionId, String status) {
        jdbc.update("INSERT INTO payments (order_id, transaction_id, status, amount) VALUES (?, ?, ?, 3800)",
                orderId, transactionId, status);
    }
}
