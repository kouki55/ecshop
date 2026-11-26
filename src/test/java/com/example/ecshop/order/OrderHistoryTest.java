package com.example.ecshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecshop.ControllablePaymentGateway;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 注文履歴（一覧・詳細）と、会員による注文のキャンセル。特に次のことを確認する。
 * <ul>
 *   <li>自分の注文だけが見え、他人の注文は見ることもキャンセルすることもできない</li>
 *   <li>キャンセルすると在庫が戻り、支払い済みなら返金される。返金できなければキャンセルしない</li>
 * </ul>
 */
@IntegrationTest
class OrderHistoryTest {

    /** 税抜 1,000 円・10% の商品 1 個：小計 1,000 + 消費税 100 + 送料 500。 */
    private static final long TOTAL_FOR_ONE = 1_600;

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
    private PaymentRepository paymentRepository;
    @Autowired
    private ControllablePaymentGateway paymentGateway;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MutableClock clock;

    private User me;
    private User other;
    private Address myAddress;
    private Address otherAddress;
    private Product product;

    @BeforeEach
    void setUp() {
        clock.reset();
        paymentGateway.reset();
        me = testUsers.active();
        other = testUsers.active();
        myAddress = testUsers.address(me, "山田太郎");
        otherAddress = testUsers.address(other, "他人花子");
        Category category = testProducts.category();
        product = testProducts.product(category, TestProducts.randomName(), 1_000, TaxRate.STANDARD, 100, true);
    }

    // ---------------------------------------------------------------- 一覧

    @Test
    void 一覧には自分の注文だけが新しい順に出る() throws Exception {
        long first = placeOrder(me, myAddress, FakePaymentGateway.SUCCESS_TOKEN);
        clock.advance(Duration.ofMinutes(1));
        long othersOrder = placeOrder(other, otherAddress, FakePaymentGateway.SUCCESS_TOKEN);
        clock.advance(Duration.ofMinutes(1));
        long second = placeOrder(me, myAddress, FakePaymentGateway.DECLINE_TOKEN);

        Page<OrderSummary> page = orderService.list(me.getId(), 0);

        assertThat(page.getContent()).extracting(OrderSummary::id).containsExactly(second, first);
        assertThat(page.getContent()).extracting(OrderSummary::status)
                .containsExactly(OrderStatus.RECEIVED, OrderStatus.PAID);
        assertThat(page.getContent().getFirst().firstProductName()).isEqualTo(product.getName());
        assertThat(page.getContent().getFirst().totalAmount()).isEqualTo(TOTAL_FOR_ONE);

        mockMvc.perform(get("/orders").with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/orders/" + first + "\"")))
                .andExpect(content().string(containsString("href=\"/orders/" + second + "\"")))
                .andExpect(content().string(not(containsString("href=\"/orders/" + othersOrder + "\""))));
    }

    @Test
    void 注文がなければその旨を表示する() throws Exception {
        mockMvc.perform(get("/orders").with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("まだご注文はありません")));
    }

    @Test
    void 商品が複数ある注文は先頭の商品名とほかの点数を出す() throws Exception {
        Product second = testProducts.product(testProducts.category(), TestProducts.randomName(), 1_000,
                TaxRate.STANDARD, 100, true);
        addToCart(me, product, 1);
        addToCart(me, second, 1);
        // 小計 2,000 + 消費税 200 + 送料 500
        place(me, myAddress, 2_700, FakePaymentGateway.SUCCESS_TOKEN);

        assertThat(orderService.list(me.getId(), 0).getContent()).singleElement().satisfies(summary ->
                assertThat(summary.itemCount()).isEqualTo(2));
        mockMvc.perform(get("/orders").with(user(TestUsers.principal(me))))
                .andExpect(content().string(containsString("ほか 1 点")));
    }

    @Test
    void 二十一件目は二ページ目に出る() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < OrderService.HISTORY_PAGE_SIZE + 1; i++) {
            ids.add(placeOrder(me, myAddress, FakePaymentGateway.SUCCESS_TOKEN));
            clock.advance(Duration.ofMinutes(1));
        }

        Page<OrderSummary> firstPage = orderService.list(me.getId(), 0);
        Page<OrderSummary> secondPage = orderService.list(me.getId(), 1);

        assertThat(firstPage.getContent()).hasSize(OrderService.HISTORY_PAGE_SIZE);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        // 新しい順なので、2 ページ目にはいちばん古い注文だけが出る
        assertThat(secondPage.getContent()).extracting(OrderSummary::id).containsExactly(ids.getFirst());
        mockMvc.perform(get("/orders").param("page", "1").with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 / 2 ページ")));
    }

    @Test
    void 範囲外のページ番号は400() throws Exception {
        mockMvc.perform(get("/orders").param("page", "-1").with(user(TestUsers.principal(me))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/orders").param("page", String.valueOf(OrderService.HISTORY_MAX_PAGE + 1))
                        .with(user(TestUsers.principal(me))))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- 詳細

    @Test
    void 詳細には注文時点の商品名と単価と金額の内訳が出る() throws Exception {
        long orderId = placeOrder(me, myAddress, FakePaymentGateway.SUCCESS_TOKEN);
        jdbc.update("UPDATE products SET name = '変更後の名前', price = 9999 WHERE id = ?", product.getId());

        mockMvc.perform(get("/orders/{id}", orderId).with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(product.getName())))
                .andExpect(content().string(not(containsString("変更後の名前"))))
                .andExpect(content().string(containsString("1,600")))
                .andExpect(content().string(containsString("支払済")))
                .andExpect(content().string(containsString("山田太郎")))
                .andExpect(content().string(containsString("この注文をキャンセルする")));
    }

    @Test
    void 受付の注文の詳細には支払い画面への案内が出る() throws Exception {
        long orderId = placeOrder(me, myAddress, FakePaymentGateway.DECLINE_TOKEN);

        mockMvc.perform(get("/orders/{id}", orderId).with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/orders/" + orderId + "/payment\"")));
    }

    @Test
    void 発送済の注文にはキャンセルの案内を出さない() throws Exception {
        long orderId = placeOrder(me, myAddress, FakePaymentGateway.SUCCESS_TOKEN);
        setStatus(orderId, OrderStatus.SHIPPED);

        mockMvc.perform(get("/orders/{id}", orderId).with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("発送済")))
                .andExpect(content().string(not(containsString("この注文をキャンセルする"))));
    }

    // ---------------------------------------------------------------- キャンセル

    @Test
    void 支払済の注文をキャンセルすると返金され在庫が戻る() throws Exception {
        long orderId = placeOrder(me, myAddress, FakePaymentGateway.SUCCESS_TOKEN);
        assertThat(stockOf(product)).isEqualTo(99);

        mockMvc.perform(get("/orders/{id}/cancel", orderId).with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("全額返金")));
        cancel(me, orderId)
                .andExpect(redirectedUrl("/orders/" + orderId))
                .andExpect(flash().attribute("orderMessage", containsString("返金しました")));

        assertThat(orderService.get(me.getId(), orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(product)).isEqualTo(100);
        assertThat(paymentRepository.findByOrderIdOrderByIdAsc(orderId)).singleElement()
                .extracting(Payment::getStatus).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(paymentGateway.refundCount()).isEqualTo(1);
    }

    @Test
    void 受付の注文をキャンセルすると返金せずに在庫が戻る() throws Exception {
        long orderId = placeOrder(me, myAddress, FakePaymentGateway.DECLINE_TOKEN);

        cancel(me, orderId)
                .andExpect(redirectedUrl("/orders/" + orderId))
                .andExpect(flash().attribute("orderMessage", "ご注文をキャンセルしました。"));

        assertThat(orderService.get(me.getId(), orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(product)).isEqualTo(100);
        assertThat(paymentGateway.refundCount()).isZero();
        // キャンセルした注文はもう支払えない
        mockMvc.perform(post("/orders/{id}/payment", orderId)
                        .param("paymentToken", FakePaymentGateway.SUCCESS_TOKEN)
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(redirectedUrl("/orders/" + orderId + "/payment"));
        assertThat(orderService.get(me.getId(), orderId).status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void 二回キャンセルしても返金と在庫の戻しは一回だけ() throws Exception {
        long orderId = placeOrder(me, myAddress, FakePaymentGateway.SUCCESS_TOKEN);

        cancel(me, orderId);
        cancel(me, orderId).andExpect(flash().attributeExists("orderError"));

        assertThat(stockOf(product)).isEqualTo(100);
        assertThat(paymentGateway.refundCount()).isEqualTo(1);
    }

    @Test
    void 返金に失敗したらキャンセルせず支払済のまま() throws Exception {
        long orderId = placeOrder(me, myAddress, FakePaymentGateway.SUCCESS_TOKEN);
        paymentGateway.failRefunds();

        cancel(me, orderId)
                .andExpect(redirectedUrl("/orders/" + orderId))
                .andExpect(flash().attribute("orderError", containsString("返金の手続きができなかった")));

        assertThat(orderService.get(me.getId(), orderId).status()).isEqualTo(OrderStatus.PAID);
        assertThat(stockOf(product)).isEqualTo(99);
        assertThat(paymentRepository.findByOrderIdOrderByIdAsc(orderId)).singleElement()
                .extracting(Payment::getStatus).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void 発送済の注文はキャンセルできない() throws Exception {
        long orderId = placeOrder(me, myAddress, FakePaymentGateway.SUCCESS_TOKEN);
        setStatus(orderId, OrderStatus.SHIPPED);

        mockMvc.perform(get("/orders/{id}/cancel", orderId).with(user(TestUsers.principal(me))))
                .andExpect(redirectedUrl("/orders/" + orderId));
        cancel(me, orderId)
                .andExpect(redirectedUrl("/orders/" + orderId))
                .andExpect(flash().attributeExists("orderError"));

        assertThat(orderService.get(me.getId(), orderId).status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(stockOf(product)).isEqualTo(99);
        assertThat(paymentGateway.refundCount()).isZero();
    }

    // ---------------------------------------------------------------- 他人のデータ・未ログイン

    @Test
    void 他人の注文の詳細とキャンセル画面は404() throws Exception {
        long othersOrder = placeOrder(other, otherAddress, FakePaymentGateway.SUCCESS_TOKEN);

        mockMvc.perform(get("/orders/{id}", othersOrder).with(user(TestUsers.principal(me))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/orders/{id}/cancel", othersOrder).with(user(TestUsers.principal(me))))
                .andExpect(status().isNotFound());
        // 存在しない ID も同じ 404（他人の注文があるかどうかを見分けられない）
        mockMvc.perform(get("/orders/{id}", Long.MAX_VALUE).with(user(TestUsers.principal(me))))
                .andExpect(status().isNotFound());
    }

    @Test
    void 他人の注文はキャンセルできない() throws Exception {
        long othersOrder = placeOrder(other, otherAddress, FakePaymentGateway.SUCCESS_TOKEN);

        cancel(me, othersOrder).andExpect(status().isNotFound());

        assertThat(orderService.get(other.getId(), othersOrder).status()).isEqualTo(OrderStatus.PAID);
        assertThat(stockOf(product)).isEqualTo(99);
        assertThat(paymentGateway.refundCount()).isZero();
    }

    @Test
    void 未ログインでは注文履歴を開けない() throws Exception {
        mockMvc.perform(get("/orders")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/orders/1")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/orders/1/cancel").with(csrf())).andExpect(redirectedUrl("/login"));
    }

    @Test
    void CSRFトークンがなければキャンセルできない() throws Exception {
        long orderId = placeOrder(me, myAddress, FakePaymentGateway.SUCCESS_TOKEN);

        mockMvc.perform(post("/orders/{id}/cancel", orderId).with(user(TestUsers.principal(me))))
                .andExpect(status().isForbidden());

        assertThat(orderService.get(me.getId(), orderId).status()).isEqualTo(OrderStatus.PAID);
    }

    // ---------------------------------------------------------------- 補助

    /** 商品を 1 個カートに入れて注文し、注文 ID を返す。 */
    private long placeOrder(User user, Address address, String paymentToken) {
        addToCart(user, product, 1);
        return switch (place(user, address, TOTAL_FOR_ONE, paymentToken)) {
            case OrderService.PlaceResult.Paid paid -> paid.orderId();
            case OrderService.PlaceResult.PaymentFailed failed -> failed.orderId();
            default -> throw new AssertionError("注文できませんでした");
        };
    }

    private OrderService.PlaceResult place(User user, Address address, long expectedTotal, String paymentToken) {
        PlaceOrderForm form = new PlaceOrderForm();
        form.setIdempotencyKey(UUID.randomUUID());
        form.setAddressId(address.getId());
        form.setExpectedTotal(expectedTotal);
        form.setPaymentToken(paymentToken);
        return orderService.place(user.getId(), form);
    }

    private void addToCart(User user, Product target, int quantity) {
        assertThat(cartService.add(user.getId(), target.getId(), quantity))
                .isInstanceOf(CartService.Result.Success.class);
    }

    private ResultActions cancel(User user, long orderId) throws Exception {
        return mockMvc.perform(post("/orders/{id}/cancel", orderId)
                .with(user(TestUsers.principal(user)))
                .with(csrf()));
    }

    /** 発送は管理画面で行う予定（未実装）なので、テストでは DB を直接書き換える。 */
    private void setStatus(long orderId, OrderStatus status) {
        jdbc.update("UPDATE orders SET status = ? WHERE id = ?", status.name(), orderId);
    }

    private int stockOf(Product target) {
        Integer stock = jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, target.getId());
        return stock == null ? -1 : stock;
    }
}
