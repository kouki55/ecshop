package com.example.ecshop.cart;

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

import com.example.ecshop.IntegrationTest;
import com.example.ecshop.MutableClock;
import com.example.ecshop.TestProducts;
import com.example.ecshop.TestUsers;
import com.example.ecshop.auth.User;
import com.example.ecshop.pricing.TaxRate;
import com.example.ecshop.product.Category;
import com.example.ecshop.product.Product;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * カートの追加・数量変更・削除と表示。特に「他人のカートを見たり操作したりできない」ことを確認する。
 */
@IntegrationTest
class CartTest {

    private static final String QUANTITY_ERROR = "数量は1〜99で指定してください。";
    private static final String STOCK_ERROR = "在庫が足りません。数量を減らしてください。";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestUsers testUsers;
    @Autowired
    private TestProducts testProducts;
    @Autowired
    private CartService cartService;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MutableClock clock;

    private User me;
    private User other;
    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        clock.reset();
        me = testUsers.active();
        other = testUsers.active();
        category = testProducts.category();
        product = withStock(200);
    }

    // ---------------------------------------------------------------- 追加

    @Test
    void 商品をカートに入れるとカート画面に表示される() throws Exception {
        add(me, product.getId(), "2")
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("cartMessage", "カートに追加しました。"));

        assertThat(items(me)).singleElement().satisfies(item -> {
            assertThat(item.getProduct().getId()).isEqualTo(product.getId());
            assertThat(item.getQuantity()).isEqualTo(2);
        });
        mockMvc.perform(get("/cart").with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(product.getName())));
    }

    @Test
    void 同じ商品をもう一度入れると数量が足される() throws Exception {
        add(me, product.getId(), "2");
        add(me, product.getId(), "3");

        assertThat(items(me)).singleElement().extracting(CartItem::getQuantity).isEqualTo(5);
    }

    @Test
    void 数量が範囲外や数字でなければ追加されず商品の画面に戻る() throws Exception {
        for (String quantity : List.of("0", "-1", "100", "abc", "")) {
            add(me, product.getId(), quantity)
                    .andExpect(redirectedUrl("/products/" + product.getId()))
                    .andExpect(flash().attribute("cartError", QUANTITY_ERROR));
        }
        assertThat(items(me)).isEmpty();
    }

    @Test
    void 足した数量が99を超えると追加されない() throws Exception {
        add(me, product.getId(), "99");

        add(me, product.getId(), "1")
                .andExpect(redirectedUrl("/products/" + product.getId()))
                .andExpect(flash().attribute("cartError", "1つの商品は99個までカートに入れられます。"));
        assertThat(items(me)).singleElement().extracting(CartItem::getQuantity).isEqualTo(99);
    }

    @Test
    void 商品の種類が30を超えると追加されない() throws Exception {
        for (int i = 0; i < CartService.MAX_ITEMS; i++) {
            cartService.add(me.getId(), withStock(10).getId(), 1);
        }

        add(me, product.getId(), "1")
                .andExpect(flash().attribute("cartError", "カートに入れられる商品は30種類までです。"));
        assertThat(items(me)).hasSize(CartService.MAX_ITEMS);

        // すでに入っている商品の数量を足すのは、種類が増えないので受け付ける
        Product first = items(me).get(0).getProduct();
        add(me, first.getId(), "1").andExpect(redirectedUrl("/cart"));
    }

    @Test
    void 在庫が足りなければ追加されない() throws Exception {
        Product few = withStock(3);
        Product soldOut = withStock(0);

        add(me, few.getId(), "4").andExpect(flash().attribute("cartError", STOCK_ERROR));
        add(me, soldOut.getId(), "1").andExpect(flash().attribute("cartError", STOCK_ERROR));
        // 足した結果が在庫を超える場合も同じ
        add(me, few.getId(), "3").andExpect(redirectedUrl("/cart"));
        add(me, few.getId(), "1").andExpect(flash().attribute("cartError", STOCK_ERROR));

        assertThat(items(me)).singleElement().extracting(CartItem::getQuantity).isEqualTo(3);
    }

    @Test
    void 非公開の商品や存在しない商品は404で商品IDが不正なら400になる() throws Exception {
        Product hidden = testProducts.unpublished(category, TestProducts.randomName());

        add(me, hidden.getId(), "1").andExpect(status().isNotFound());
        add(me, Long.MAX_VALUE, "1").andExpect(status().isNotFound());
        mockMvc.perform(post("/cart/items").param("productId", "abc").param("quantity", "1")
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/cart/items").param("quantity", "1")
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(items(me)).isEmpty();
    }

    // ---------------------------------------------------------------- 数量変更・削除

    @Test
    void 数量を変更できる() throws Exception {
        cartService.add(me.getId(), product.getId(), 1);
        CartItem item = items(me).get(0);

        change(me, item.getId(), "7")
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("cartMessage", "数量を変更しました。"));
        assertThat(items(me).get(0).getQuantity()).isEqualTo(7);
    }

    @Test
    void 数量の変更でも範囲外と在庫超えは受け付けない() throws Exception {
        Product few = withStock(3);
        cartService.add(me.getId(), few.getId(), 1);
        CartItem item = items(me).get(0);

        change(me, item.getId(), "0").andExpect(flash().attribute("cartError", QUANTITY_ERROR));
        change(me, item.getId(), "abc").andExpect(flash().attribute("cartError", QUANTITY_ERROR));
        change(me, item.getId(), "4").andExpect(flash().attribute("cartError", STOCK_ERROR));
        assertThat(items(me).get(0).getQuantity()).isEqualTo(1);
    }

    @Test
    void 販売終了になった商品は数量を変更できない() throws Exception {
        cartService.add(me.getId(), product.getId(), 1);
        CartItem item = items(me).get(0);
        jdbc.update("UPDATE products SET published = FALSE WHERE id = ?", product.getId());

        change(me, item.getId(), "2").andExpect(flash().attribute("cartError", "この商品は販売を終了しました。"));
        assertThat(items(me).get(0).getQuantity()).isEqualTo(1);
    }

    @Test
    void カートから削除できる() throws Exception {
        cartService.add(me.getId(), product.getId(), 1);
        CartItem item = items(me).get(0);

        mockMvc.perform(post("/cart/items/{id}/delete", item.getId())
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("cartMessage", "カートから削除しました。"));
        assertThat(items(me)).isEmpty();
    }

    // ---------------------------------------------------------------- 他人のカート

    @Test
    void 他人の明細IDでは数量を変更できず404になる() throws Exception {
        CartItem othersItem = othersItem();

        change(me, othersItem.getId(), "5").andExpect(status().isNotFound());
        // 入力エラーになる値でも、先に持ち主を確かめて 404 にする
        change(me, othersItem.getId(), "0").andExpect(status().isNotFound());

        assertThat(items(other)).singleElement().extracting(CartItem::getQuantity).isEqualTo(1);
    }

    @Test
    void 他人の明細IDでは削除できず404になる() throws Exception {
        CartItem othersItem = othersItem();

        mockMvc.perform(post("/cart/items/{id}/delete", othersItem.getId())
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(items(other)).hasSize(1);
    }

    @Test
    void 存在しない明細IDも他人の明細と同じ404になる() throws Exception {
        change(me, Long.MAX_VALUE, "1").andExpect(status().isNotFound());
        mockMvc.perform(post("/cart/items/{id}/delete", Long.MAX_VALUE)
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void カート画面には自分の明細だけが表示される() throws Exception {
        Product mine = withStock(10);
        cartService.add(me.getId(), mine.getId(), 1);
        CartItem othersItem = othersItem();

        mockMvc.perform(get("/cart").with(user(TestUsers.principal(me))))
                .andExpect(content().string(containsString(mine.getName())))
                .andExpect(content().string(not(containsString(othersItem.getProduct().getName()))));
    }

    @Test
    void フォームに他人の会員IDを紛れ込ませても自分のカートに入る() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .param("productId", String.valueOf(product.getId()))
                        .param("quantity", "1")
                        .param("userId", String.valueOf(other.getId()))
                        .param("cartId", "1")
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(redirectedUrl("/cart"));

        assertThat(items(me)).hasSize(1);
        assertThat(items(other)).isEmpty();
    }

    // ---------------------------------------------------------------- 表示と金額

    @Test
    void 金額は税率ごとに1回だけ切り捨てて計算される() throws Exception {
        // 999 円 × 2 個：消費税は 1,998 × 10% = 199.8 → 199 円、送料 500 円、合計 2,697 円
        Product cheap = testProducts.product(category, TestProducts.randomName(), 999, TaxRate.STANDARD, 10, true);
        cartService.add(me.getId(), cheap.getId(), 2);

        CartView cart = cartView(me);
        assertThat(cart.price().subtotal()).isEqualTo(1998);
        assertThat(cart.price().taxAmount()).isEqualTo(199);
        assertThat(cart.price().shippingFee()).isEqualTo(500);
        assertThat(cart.price().total()).isEqualTo(2697);
        mockMvc.perform(get("/cart").with(user(TestUsers.principal(me))))
                .andExpect(content().string(containsString("2,697")));
    }

    @Test
    void あとから販売終了や在庫切れになった商品は警告付きで表示され合計に含まれない() throws Exception {
        Product discontinued = withStock(10);
        Product soldOut = withStock(10);
        Product fewLeft = withStock(10);
        Product ok = testProducts.product(category, TestProducts.randomName(), 1000, TaxRate.STANDARD, 10, true);
        cartService.add(me.getId(), discontinued.getId(), 1);
        cartService.add(me.getId(), soldOut.getId(), 1);
        cartService.add(me.getId(), fewLeft.getId(), 5);
        cartService.add(me.getId(), ok.getId(), 1);
        jdbc.update("UPDATE products SET published = FALSE WHERE id = ?", discontinued.getId());
        jdbc.update("UPDATE products SET stock = 0 WHERE id = ?", soldOut.getId());
        jdbc.update("UPDATE products SET stock = 2 WHERE id = ?", fewLeft.getId());

        CartView cart = cartView(me);
        assertThat(cart.lines()).extracting(CartView.Line::status).containsExactly(
                LineStatus.DISCONTINUED, LineStatus.OUT_OF_STOCK, LineStatus.INSUFFICIENT_STOCK, LineStatus.OK);
        // 合計は OK の 1 行（税抜 1,000 円）だけで計算する
        assertThat(cart.price().subtotal()).isEqualTo(1000);
        mockMvc.perform(get("/cart").with(user(TestUsers.principal(me))))
                .andExpect(content().string(containsString("販売終了")))
                .andExpect(content().string(containsString("在庫切れ")))
                .andExpect(content().string(containsString("在庫不足")))
                .andExpect(content().string(containsString("合計に含めていません")));
    }

    @Test
    void カートが空なら空と表示される() throws Exception {
        mockMvc.perform(get("/cart").with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("カートに商品はありません")));
    }

    @Test
    void 商品詳細画面にカートに入れるボタンがあり在庫切れなら出ない() throws Exception {
        Product soldOut = withStock(0);

        mockMvc.perform(get("/products/{id}", product.getId()))
                .andExpect(content().string(containsString(">カートに入れる</button>")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mockMvc.perform(get("/products/{id}", soldOut.getId()))
                .andExpect(content().string(not(containsString(">カートに入れる</button>"))));
    }

    // ---------------------------------------------------------------- アクセス制御

    @Test
    void 未ログインではカートを使えずログイン画面へ送られる() throws Exception {
        mockMvc.perform(get("/cart")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/cart/items").param("productId", String.valueOf(product.getId()))
                        .param("quantity", "1").with(csrf()))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 管理者はカートを使えない() throws Exception {
        mockMvc.perform(get("/cart").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void CSRFトークンがない送信は拒否される() throws Exception {
        mockMvc.perform(post("/cart/items").param("productId", String.valueOf(product.getId()))
                        .param("quantity", "1").with(user(TestUsers.principal(me))))
                .andExpect(status().isForbidden());
        assertThat(items(me)).isEmpty();
    }

    // ---------------------------------------------------------------- 退会

    @Test
    void 退会するとカートが削除される() throws Exception {
        cartService.add(me.getId(), product.getId(), 1);
        CartItem othersItem = othersItem();

        mockMvc.perform(post("/account/withdraw").param("password", TestUsers.PASSWORD)
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(redirectedUrl("/withdrawn"));

        assertThat(cartRepository.findByUserId(me.getId())).isEmpty();
        assertThat(items(me)).isEmpty();
        // ほかの会員のカートは残る
        assertThat(items(other)).extracting(CartItem::getId).containsExactly(othersItem.getId());
    }

    // ---------------------------------------------------------------- 補助

    private Product withStock(int stock) {
        return testProducts.product(category, TestProducts.randomName(), 1000, TaxRate.STANDARD, stock, true);
    }

    private CartItem othersItem() {
        Product product = withStock(10);
        cartService.add(other.getId(), product.getId(), 1);
        return items(other).get(0);
    }

    private List<CartItem> items(User owner) {
        return cartItemRepository.findByCartUserIdOrderByIdAsc(owner.getId());
    }

    private CartView cartView(User owner) throws Exception {
        return (CartView) mockMvc.perform(get("/cart").with(user(TestUsers.principal(owner))))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("cart");
    }

    private ResultActions add(User owner, long productId, String quantity) throws Exception {
        return mockMvc.perform(post("/cart/items")
                .param("productId", String.valueOf(productId))
                .param("quantity", quantity)
                .with(user(TestUsers.principal(owner))).with(csrf()));
    }

    private ResultActions change(User owner, long itemId, String quantity) throws Exception {
        return mockMvc.perform(post("/cart/items/{id}", itemId)
                .param("quantity", quantity)
                .with(user(TestUsers.principal(owner))).with(csrf()));
    }
}
