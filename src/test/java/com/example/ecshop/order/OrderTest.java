package com.example.ecshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecshop.CapturingMailSender;
import com.example.ecshop.IntegrationTest;
import com.example.ecshop.MutableClock;
import com.example.ecshop.TestProducts;
import com.example.ecshop.TestUsers;
import com.example.ecshop.auth.Address;
import com.example.ecshop.auth.User;
import com.example.ecshop.cart.CartService;
import com.example.ecshop.payment.FakePaymentGateway;
import com.example.ecshop.payment.Payment;
import com.example.ecshop.payment.PaymentRepository;
import com.example.ecshop.payment.PaymentStatus;
import com.example.ecshop.pricing.TaxRate;
import com.example.ecshop.product.Category;
import com.example.ecshop.product.Product;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 注文の確認・確定・支払い・自動キャンセル。特に次のことを確認する。
 * <ul>
 *   <li>二重送信や同時の注文で、注文・請求・在庫がおかしくならない</li>
 *   <li>他人の注文・住所を見たり操作したりできない</li>
 * </ul>
 */
@IntegrationTest
class OrderTest {

    /** 税抜 1,000 円・10% の商品 2 個：小計 2,000 + 消費税 200 + 送料 500。 */
    private static final long TOTAL_FOR_TWO = 2_700;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestUsers testUsers;
    @Autowired
    private TestProducts testProducts;
    @Autowired
    private CartService cartService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private UnpaidOrderCanceller canceller;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private CapturingMailSender mailSender;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MutableClock clock;

    private User me;
    private User other;
    private Address myAddress;
    private Address otherAddress;
    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        clock.reset();
        me = testUsers.active();
        other = testUsers.active();
        myAddress = testUsers.address(me, "山田太郎");
        otherAddress = testUsers.address(other, "他人花子");
        category = testProducts.category();
        product = testProducts.published(category, TestProducts.randomName());
    }

    // ---------------------------------------------------------------- 配送先・確認画面

    @Test
    void カートが空なら配送先の画面に進めずカートに戻される() throws Exception {
        mockMvc.perform(get("/orders/new").with(user(TestUsers.principal(me))))
                .andExpect(redirectedUrl("/cart"));
    }

    @Test
    void 配送先の画面に自分の住所だけが表示される() throws Exception {
        addToCart(me, product, 1);
        mockMvc.perform(get("/orders/new").with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("山田太郎")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("他人花子"))));
    }

    @Test
    void 確認画面に金額と支払時期とお届け予定とキャンセル条件が表示される() throws Exception {
        addToCart(me, product, 2);
        String deliveryLabel = DeliveryEstimate.from(clock.instant()).label();

        mockMvc.perform(get("/orders/confirm").param("addressId", myAddress.getId().toString())
                        .with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(product.getName())))
                .andExpect(content().string(containsString("2,700")))
                .andExpect(content().string(containsString("注文確定時にお支払いいただきます")))
                .andExpect(content().string(containsString(deliveryLabel)))
                .andExpect(content().string(containsString("発送前であればキャンセルできます")))
                .andExpect(content().string(containsString("name=\"idempotencyKey\"")));
    }

    @Test
    void 他人の住所では確認画面を開けない() throws Exception {
        addToCart(me, product, 1);
        mockMvc.perform(get("/orders/confirm").param("addressId", otherAddress.getId().toString())
                        .with(user(TestUsers.principal(me))))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- 確定

    @Test
    void 注文を確定すると支払済になり在庫が減りカートが空になる() throws Exception {
        addToCart(me, product, 2);

        place(me, UUID.randomUUID(), myAddress, TOTAL_FOR_TWO, FakePaymentGateway.SUCCESS_TOKEN)
                .andExpect(redirectedUrl("/orders/" + onlyOrderId(me) + "/complete"));

        long orderId = onlyOrderId(me);
        OrderView order = orderService.get(me.getId(), orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.subtotal()).isEqualTo(2_000);
        assertThat(order.taxAmount()).isEqualTo(200);
        assertThat(order.shippingFee()).isEqualTo(500);
        assertThat(order.totalAmount()).isEqualTo(TOTAL_FOR_TWO);
        assertThat(order.recipientName()).isEqualTo("山田太郎");
        assertThat(order.lines()).singleElement().satisfies(line -> {
            assertThat(line.productName()).isEqualTo(product.getName());
            assertThat(line.unitPrice()).isEqualTo(1_000);
            assertThat(line.taxRatePercent()).isEqualTo(10);
            assertThat(line.quantity()).isEqualTo(2);
        });
        assertThat(stockOf(product)).isEqualTo(8);
        assertThat(cartService.orderLines(me.getId())).isEmpty();
        assertThat(paymentRepository.findByOrderIdOrderByIdAsc(orderId))
                .extracting(Payment::getStatus, Payment::getAmount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(PaymentStatus.SUCCEEDED, TOTAL_FOR_TWO));

        mockMvc.perform(get("/orders/{id}/complete", orderId).with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ご注文ありがとうございます")))
                .andExpect(content().string(containsString(product.getName())));
    }

    @Test
    void 支払いが済むと確認メールが届く() throws Exception {
        addToCart(me, product, 2);
        place(me, UUID.randomUUID(), myAddress, TOTAL_FOR_TWO, FakePaymentGateway.SUCCESS_TOKEN);

        assertThat(mailSender.sentTo(me.getEmail())).singleElement().satisfies(mail -> {
            assertThat(mail.getSubject()).contains("ご注文ありがとうございます");
            assertThat(mail.getText()).contains(product.getName(), "2,700円", "山田太郎",
                    DeliveryEstimate.from(clock.instant()).label());
        });
    }

    @Test
    void あとで商品を変更しても注文の明細は変わらない() throws Exception {
        addToCart(me, product, 2);
        place(me, UUID.randomUUID(), myAddress, TOTAL_FOR_TWO, FakePaymentGateway.SUCCESS_TOKEN);

        jdbc.update("UPDATE products SET name = '変更後の名前', price = 9999, tax_rate = 8 WHERE id = ?",
                product.getId());

        assertThat(orderService.get(me.getId(), onlyOrderId(me)).lines()).singleElement().satisfies(line -> {
            assertThat(line.productName()).isEqualTo(product.getName());
            assertThat(line.unitPrice()).isEqualTo(1_000);
            assertThat(line.taxRatePercent()).isEqualTo(10);
        });
    }

    @Test
    void 同じ冪等キーで2回送っても注文は1件だけで請求も1回() throws Exception {
        addToCart(me, product, 2);
        UUID key = UUID.randomUUID();

        place(me, key, myAddress, TOTAL_FOR_TWO, FakePaymentGateway.SUCCESS_TOKEN);
        long orderId = onlyOrderId(me);
        // 2 回目（カートは空になっているが、同じキーなので先の注文の画面に案内される）
        place(me, key, myAddress, TOTAL_FOR_TWO, FakePaymentGateway.SUCCESS_TOKEN)
                .andExpect(redirectedUrl("/orders/" + orderId + "/complete"));

        assertThat(orderIds(me)).containsExactly(orderId);
        assertThat(paymentRepository.countByOrderId(orderId)).isEqualTo(1);
        assertThat(stockOf(product)).isEqualTo(8);
    }

    @Test
    void 在庫が足りなければ注文できずカートに戻される() throws Exception {
        addToCart(me, product, 2);
        jdbc.update("UPDATE products SET stock = 1 WHERE id = ?", product.getId());

        place(me, UUID.randomUUID(), myAddress, TOTAL_FOR_TWO, FakePaymentGateway.SUCCESS_TOKEN)
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attributeExists("cartError"));

        assertThat(orderIds(me)).isEmpty();
        assertThat(stockOf(product)).isEqualTo(1);
    }

    @Test
    void 販売終了の商品があれば注文できない() throws Exception {
        addToCart(me, product, 2);
        jdbc.update("UPDATE products SET published = FALSE WHERE id = ?", product.getId());

        place(me, UUID.randomUUID(), myAddress, TOTAL_FOR_TWO, FakePaymentGateway.SUCCESS_TOKEN)
                .andExpect(redirectedUrl("/cart"));

        assertThat(orderIds(me)).isEmpty();
        assertThat(stockOf(product)).isEqualTo(10);
    }

    @Test
    void 確認画面のあとで金額が変わったら注文せず確認画面に戻す() throws Exception {
        addToCart(me, product, 2);
        jdbc.update("UPDATE products SET price = 1100 WHERE id = ?", product.getId());

        place(me, UUID.randomUUID(), myAddress, TOTAL_FOR_TWO, FakePaymentGateway.SUCCESS_TOKEN)
                .andExpect(redirectedUrl("/orders/confirm?addressId=" + myAddress.getId()))
                .andExpect(flash().attribute("orderError",
                        "ご注文の金額が変わりました。内容をご確認のうえ、もう一度確定してください。"));

        assertThat(orderIds(me)).isEmpty();
        assertThat(stockOf(product)).isEqualTo(10);
    }

    @Test
    void 選択肢にない支払い方法は400() throws Exception {
        addToCart(me, product, 2);
        place(me, UUID.randomUUID(), myAddress, TOTAL_FOR_TWO, "4111111111111111")
                .andExpect(status().isBadRequest());
        assertThat(orderIds(me)).isEmpty();
    }

    @Test
    void 消費税は税率ごとの合計に対して1回だけ切り捨てる() throws Exception {
        // 税抜 155 円（10%）× 3 = 465 → 消費税 46.5 → 46 円。送料 500 円
        Product cheap = testProducts.product(category, TestProducts.randomName(), 155, TaxRate.STANDARD, 10, true);
        addToCart(me, cheap, 3);

        place(me, UUID.randomUUID(), myAddress, 465 + 46 + 500, FakePaymentGateway.SUCCESS_TOKEN);

        OrderView order = orderService.get(me.getId(), onlyOrderId(me));
        assertThat(order.taxAmount()).isEqualTo(46);
        assertThat(order.totalAmount()).isEqualTo(1_011);
    }

    // ---------------------------------------------------------------- 支払いの失敗と再試行

    @Test
    void 支払いに失敗すると受付のまま支払い画面に送られ再試行で支払える() throws Exception {
        addToCart(me, product, 2);

        place(me, UUID.randomUUID(), myAddress, TOTAL_FOR_TWO, FakePaymentGateway.DECLINE_TOKEN)
                .andExpect(redirectedUrl("/orders/" + onlyOrderId(me) + "/payment"))
                .andExpect(flash().attributeExists("orderError"));
        long orderId = onlyOrderId(me);
        assertThat(orderService.get(me.getId(), orderId).status()).isEqualTo(OrderStatus.RECEIVED);
        // 在庫は確定の時点で確保済み
        assertThat(stockOf(product)).isEqualTo(8);
        assertThat(mailSender.sentTo(me.getEmail())).isEmpty();
        // 支払われていない注文に完了画面は出さない
        mockMvc.perform(get("/orders/{id}/complete", orderId).with(user(TestUsers.principal(me))))
                .andExpect(redirectedUrl("/orders/" + orderId + "/payment"));

        pay(me, orderId, FakePaymentGateway.SUCCESS_TOKEN)
                .andExpect(redirectedUrl("/orders/" + orderId + "/complete"));

        assertThat(orderService.get(me.getId(), orderId).status()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findByOrderIdOrderByIdAsc(orderId))
                .extracting(Payment::getStatus)
                .containsExactly(PaymentStatus.FAILED, PaymentStatus.SUCCEEDED);
        assertThat(mailSender.sentTo(me.getEmail())).hasSize(1);
        assertThat(stockOf(product)).isEqualTo(8);
    }

    @Test
    void 支払済の注文はもう一度支払えない() throws Exception {
        addToCart(me, product, 2);
        place(me, UUID.randomUUID(), myAddress, TOTAL_FOR_TWO, FakePaymentGateway.SUCCESS_TOKEN);
        long orderId = onlyOrderId(me);

        pay(me, orderId, FakePaymentGateway.SUCCESS_TOKEN)
                .andExpect(redirectedUrl("/orders/" + orderId + "/complete"));

        assertThat(paymentRepository.countByOrderId(orderId)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 自動キャンセル

    @Test
    void 支払い期限を過ぎた注文は自動でキャンセルされ在庫が戻る() throws Exception {
        addToCart(me, product, 2);
        place(me, UUID.randomUUID(), myAddress, TOTAL_FOR_TWO, FakePaymentGateway.DECLINE_TOKEN);
        long orderId = onlyOrderId(me);

        // 期限の時刻ちょうどまでは支払えるので、まだキャンセルしない
        clock.advance(Duration.ofMinutes(30));
        canceller.run();
        assertThat(orderService.get(me.getId(), orderId).status()).isEqualTo(OrderStatus.RECEIVED);

        clock.advance(Duration.ofSeconds(1));
        canceller.run();
        assertThat(orderService.get(me.getId(), orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(product)).isEqualTo(10);

        // キャンセルされた注文は支払えない（請求もしない）
        pay(me, orderId, FakePaymentGateway.SUCCESS_TOKEN)
                .andExpect(redirectedUrl("/orders/" + orderId + "/payment"));
        assertThat(paymentRepository.countByOrderId(orderId)).isEqualTo(1);
        mockMvc.perform(get("/orders/{id}/payment", orderId).with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("このご注文はキャンセルされました")));
    }

    @Test
    void 支払済の注文は期限を過ぎてもキャンセルされない() throws Exception {
        addToCart(me, product, 2);
        place(me, UUID.randomUUID(), myAddress, TOTAL_FOR_TWO, FakePaymentGateway.SUCCESS_TOKEN);

        clock.advance(Duration.ofHours(1));
        canceller.run();

        assertThat(orderService.get(me.getId(), onlyOrderId(me)).status()).isEqualTo(OrderStatus.PAID);
        assertThat(stockOf(product)).isEqualTo(8);
    }

    @Test
    void 期限を過ぎてから支払おうとするとその場でキャンセルされ請求しない() throws Exception {
        addToCart(me, product, 2);
        place(me, UUID.randomUUID(), myAddress, TOTAL_FOR_TWO, FakePaymentGateway.DECLINE_TOKEN);
        long orderId = onlyOrderId(me);

        clock.advance(Duration.ofMinutes(31));
        pay(me, orderId, FakePaymentGateway.SUCCESS_TOKEN)
                .andExpect(redirectedUrl("/orders/" + orderId + "/payment"));

        assertThat(orderService.get(me.getId(), orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(paymentRepository.countByOrderId(orderId)).isEqualTo(1);
        assertThat(stockOf(product)).isEqualTo(10);
    }

    // ---------------------------------------------------------------- 同時の注文

    @Test
    void 残り1個を2人が同時に注文しても成功するのは1人だけ() throws Exception {
        Product lastOne = testProducts.product(category, TestProducts.randomName(), 1000, TaxRate.STANDARD, 1, true);
        addToCart(me, lastOne, 1);
        addToCart(other, lastOne, 1);
        long total = 1_000 + 100 + 500;

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<OrderService.PlaceResult> mine = executor.submit(() -> {
                start.await();
                return orderService.place(me.getId(), form(UUID.randomUUID(), myAddress, total));
            });
            Future<OrderService.PlaceResult> others = executor.submit(() -> {
                start.await();
                return orderService.place(other.getId(), form(UUID.randomUUID(), otherAddress, total));
            });
            start.countDown();

            List<OrderService.PlaceResult> results = List.of(mine.get(), others.get());
            assertThat(results).filteredOn(r -> r instanceof OrderService.PlaceResult.Paid).hasSize(1);
            assertThat(results).filteredOn(r -> r instanceof OrderService.PlaceResult.CartNotReady).hasSize(1);
        } finally {
            executor.shutdown();
        }
        assertThat(stockOf(lastOne)).isZero();
    }

    // ---------------------------------------------------------------- 他人のデータ

    @Test
    void 他人の注文の完了画面と支払い画面は404() throws Exception {
        addToCart(other, product, 2);
        place(other, UUID.randomUUID(), otherAddress, TOTAL_FOR_TWO, FakePaymentGateway.DECLINE_TOKEN);
        long othersOrderId = onlyOrderId(other);

        mockMvc.perform(get("/orders/{id}/complete", othersOrderId).with(user(TestUsers.principal(me))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/orders/{id}/payment", othersOrderId).with(user(TestUsers.principal(me))))
                .andExpect(status().isNotFound());
    }

    @Test
    void 他人の注文は支払えない() throws Exception {
        addToCart(other, product, 2);
        place(other, UUID.randomUUID(), otherAddress, TOTAL_FOR_TWO, FakePaymentGateway.DECLINE_TOKEN);
        long othersOrderId = onlyOrderId(other);

        pay(me, othersOrderId, FakePaymentGateway.SUCCESS_TOKEN).andExpect(status().isNotFound());
        // 入力エラーのときも、先に持ち主を確かめて 404 にする
        pay(me, othersOrderId, "").andExpect(status().isNotFound());

        assertThat(paymentRepository.countByOrderId(othersOrderId)).isEqualTo(1);
        assertThat(orderService.get(other.getId(), othersOrderId).status()).isEqualTo(OrderStatus.RECEIVED);
    }

    @Test
    void 他人の住所を指定して注文はできない() throws Exception {
        addToCart(me, product, 2);

        place(me, UUID.randomUUID(), otherAddress, TOTAL_FOR_TWO, FakePaymentGateway.SUCCESS_TOKEN)
                .andExpect(status().isNotFound());

        assertThat(orderIds(me)).isEmpty();
        assertThat(stockOf(product)).isEqualTo(10);
    }

    @Test
    void 他人の注文の冪等キーを使っても他人の注文は見えない() throws Exception {
        addToCart(other, product, 2);
        UUID othersKey = UUID.randomUUID();
        place(other, othersKey, otherAddress, TOTAL_FOR_TWO, FakePaymentGateway.SUCCESS_TOKEN);
        addToCart(me, product, 2);

        place(me, othersKey, myAddress, TOTAL_FOR_TWO, FakePaymentGateway.SUCCESS_TOKEN)
                .andExpect(status().isBadRequest());

        assertThat(orderIds(me)).isEmpty();
    }

    @Test
    void 未ログインでは注文の画面を開けない() throws Exception {
        mockMvc.perform(get("/orders/new")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/orders/1/complete")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/orders").with(csrf())).andExpect(redirectedUrl("/login"));
    }

    @Test
    void CSRFトークンがなければ注文できない() throws Exception {
        addToCart(me, product, 2);
        mockMvc.perform(post("/orders")
                        .param("idempotencyKey", UUID.randomUUID().toString())
                        .param("addressId", myAddress.getId().toString())
                        .param("expectedTotal", String.valueOf(TOTAL_FOR_TWO))
                        .param("paymentToken", FakePaymentGateway.SUCCESS_TOKEN)
                        .with(user(TestUsers.principal(me))))
                .andExpect(status().isForbidden());
        assertThat(orderIds(me)).isEmpty();
    }

    // ---------------------------------------------------------------- 補助

    private void addToCart(User user, Product target, int quantity) {
        assertThat(cartService.add(user.getId(), target.getId(), quantity))
                .isInstanceOf(CartService.Result.Success.class);
    }

    private ResultActions place(User user, UUID key, Address address, long expectedTotal, String paymentToken)
            throws Exception {
        return mockMvc.perform(post("/orders")
                .param("idempotencyKey", key.toString())
                .param("addressId", address.getId().toString())
                .param("expectedTotal", String.valueOf(expectedTotal))
                .param("paymentToken", paymentToken)
                .with(user(TestUsers.principal(user)))
                .with(csrf()));
    }

    private ResultActions pay(User user, long orderId, String paymentToken) throws Exception {
        return mockMvc.perform(post("/orders/{id}/payment", orderId)
                .param("paymentToken", paymentToken)
                .with(user(TestUsers.principal(user)))
                .with(csrf()));
    }

    private static PlaceOrderForm form(UUID key, Address address, long expectedTotal) {
        PlaceOrderForm form = new PlaceOrderForm();
        form.setIdempotencyKey(key);
        form.setAddressId(address.getId());
        form.setExpectedTotal(expectedTotal);
        form.setPaymentToken(FakePaymentGateway.SUCCESS_TOKEN);
        return form;
    }

    private List<Long> orderIds(User user) {
        return jdbc.queryForList("SELECT id FROM orders WHERE user_id = ? ORDER BY id", Long.class, user.getId());
    }

    private long onlyOrderId(User user) {
        List<Long> ids = orderIds(user);
        assertThat(ids).hasSize(1);
        return ids.getFirst();
    }

    private int stockOf(Product target) {
        Integer stock = jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, target.getId());
        return stock == null ? -1 : stock;
    }
}
