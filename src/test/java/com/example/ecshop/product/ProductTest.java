package com.example.ecshop.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.ecshop.IntegrationTest;
import com.example.ecshop.MutableClock;
import com.example.ecshop.TestProducts;
import com.example.ecshop.TestUsers;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 商品一覧と詳細。特に「非公開の商品が見えない」「利用者が件数や並び順を操作できない」ことを確認する。
 */
@IntegrationTest
class ProductTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestProducts testProducts;
    @Autowired
    private TestUsers testUsers;
    @Autowired
    private MutableClock clock;

    private Category category;

    @BeforeEach
    void setUp() {
        clock.reset();
        category = testProducts.category();
    }

    // ---------------------------------------------------------------- 閲覧

    @Test
    void 未ログインでも商品の一覧と詳細が見られる() throws Exception {
        Product product = testProducts.published(category, TestProducts.randomName());

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("product/list"));
        mockMvc.perform(get("/products").param("category", String.valueOf(category.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(product.getName())));
        mockMvc.perform(get("/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("product/detail"))
                .andExpect(content().string(containsString(product.getName())))
                .andExpect(content().string(containsString(category.getName())));
    }

    @Test
    void 会員もログインしたまま商品を見られる() throws Exception {
        Product product = testProducts.published(category, TestProducts.randomName());

        mockMvc.perform(get("/products/{id}", product.getId())
                        .with(user(TestUsers.principal(testUsers.active()))))
                .andExpect(status().isOk());
    }

    @Test
    void 新しい商品ほど先に表示される() throws Exception {
        Product older = testProducts.published(category, TestProducts.randomName());
        clock.advance(Duration.ofMinutes(1));
        Product newer = testProducts.published(category, TestProducts.randomName());

        List<String> names = page(get("/products").param("category", String.valueOf(category.getId())))
                .getContent().stream().map(p -> ((ProductView) p).name()).toList();
        assertThat(names).containsExactly(newer.getName(), older.getName());
    }

    // ---------------------------------------------------------------- 非公開・存在しない

    @Test
    void 非公開の商品は一覧に出ない() throws Exception {
        Product visible = testProducts.published(category, TestProducts.randomName());
        Product hidden = testProducts.unpublished(category, TestProducts.randomName());

        mockMvc.perform(get("/products").param("category", String.valueOf(category.getId())))
                .andExpect(content().string(containsString(visible.getName())))
                .andExpect(content().string(not(containsString(hidden.getName()))));
        // カテゴリで絞り込まない一覧にも出ない
        mockMvc.perform(get("/products"))
                .andExpect(content().string(not(containsString(hidden.getName()))));
    }

    @Test
    void 非公開の商品の詳細は存在しない商品と同じ404になる() throws Exception {
        Product hidden = testProducts.unpublished(category, TestProducts.randomName());

        mockMvc.perform(get("/products/{id}", hidden.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(hidden.getName()))));
        mockMvc.perform(get("/products/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
        // ログインしていても同じ
        mockMvc.perform(get("/products/{id}", hidden.getId())
                        .with(user(TestUsers.principal(testUsers.active()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void 数字でない商品IDは400になる() throws Exception {
        mockMvc.perform(get("/products/abc")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- カテゴリ

    @Test
    void カテゴリで絞り込むとそのカテゴリの商品だけが出る() throws Exception {
        Category otherCategory = testProducts.category();
        Product mine = testProducts.published(category, TestProducts.randomName());
        Product others = testProducts.published(otherCategory, TestProducts.randomName());

        mockMvc.perform(get("/products").param("category", String.valueOf(category.getId())))
                .andExpect(content().string(containsString(mine.getName())))
                .andExpect(content().string(not(containsString(others.getName()))));
    }

    @Test
    void 存在しないカテゴリは404で数字でないカテゴリは400になる() throws Exception {
        mockMvc.perform(get("/products").param("category", String.valueOf(Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/products").param("category", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 商品のないカテゴリでは商品がないと表示される() throws Exception {
        testProducts.unpublished(category, TestProducts.randomName());

        mockMvc.perform(get("/products").param("category", String.valueOf(category.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("商品がありません")));
    }

    // ---------------------------------------------------------------- ページ分け

    @Test
    void 一覧は20件ずつに分かれる() throws Exception {
        createPublished(ProductService.PAGE_SIZE + 1);
        String categoryId = String.valueOf(category.getId());

        Page<?> first = page(get("/products").param("category", categoryId));
        assertThat(first.getContent()).hasSize(ProductService.PAGE_SIZE);
        assertThat(first.getTotalElements()).isEqualTo(ProductService.PAGE_SIZE + 1);

        Page<?> second = page(get("/products").param("category", categoryId).param("page", "1"));
        assertThat(second.getContent()).hasSize(1);
    }

    @Test
    void 次のページへのリンクはカテゴリの絞り込みを保つ() throws Exception {
        createPublished(ProductService.PAGE_SIZE + 1);

        mockMvc.perform(get("/products").param("category", String.valueOf(category.getId())))
                .andExpect(content().string(containsString(
                        "/products?category=" + category.getId() + "&amp;page=1")));
    }

    @Test
    void 件数や並び順をURLで変えられない() throws Exception {
        createPublished(ProductService.PAGE_SIZE + 1);
        String categoryId = String.valueOf(category.getId());

        // size を大きくしても 1 ページの件数は変わらない
        Page<?> page = page(get("/products").param("category", categoryId).param("size", "1000"));
        assertThat(page.getContent()).hasSize(ProductService.PAGE_SIZE);

        // 存在しない項目で並べ替えようとしてもエラーにならず、無視される
        mockMvc.perform(get("/products").param("category", categoryId).param("sort", "passwordHash,desc"))
                .andExpect(status().isOk());
    }

    @Test
    void 範囲外のページ番号は400になる() throws Exception {
        mockMvc.perform(get("/products").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/products").param("page", String.valueOf(ProductService.MAX_PAGE + 1)))
                .andExpect(status().isBadRequest());
        // int に収まらない数や数字でない値も 400
        mockMvc.perform(get("/products").param("page", "99999999999"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/products").param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 最後より後のページは商品がないと表示される() throws Exception {
        testProducts.published(category, TestProducts.randomName());

        mockMvc.perform(get("/products").param("category", String.valueOf(category.getId())).param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("商品がありません")));
    }

    // ---------------------------------------------------------------- 表示内容

    @Test
    void 商品名に含まれるHTMLはエスケープされる() throws Exception {
        Product product = testProducts.published(category, "<script>alert('xss')</script>");

        mockMvc.perform(get("/products/{id}", product.getId()))
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
        mockMvc.perform(get("/products").param("category", String.valueOf(category.getId())))
                .andExpect(content().string(not(containsString("<script>alert"))));
    }

    @Test
    void 税込価格と税抜価格が表示される() throws Exception {
        // 999 × 1.10 = 1,098.9 → 切り捨てて 1,098 円
        Product standard = testProducts.product(category, TestProducts.randomName(), 999, TaxRate.STANDARD, 10, true);
        // 999 × 1.08 = 1,078.92 → 切り捨てて 1,078 円
        Product reduced = testProducts.product(category, TestProducts.randomName(), 999, TaxRate.REDUCED, 10, true);

        mockMvc.perform(get("/products/{id}", standard.getId()))
                .andExpect(content().string(containsString("1,098")))
                .andExpect(content().string(containsString("999")))
                .andExpect(content().string(containsString("10</span>%")));
        mockMvc.perform(get("/products/{id}", reduced.getId()))
                .andExpect(content().string(containsString("1,078")))
                .andExpect(content().string(containsString("8</span>%")));
    }

    @Test
    void 在庫数に応じて在庫の状態が表示され個数は出ない() throws Exception {
        Product soldOut = testProducts.product(category, TestProducts.randomName(), 1000, TaxRate.STANDARD, 0, true);
        Product low = testProducts.product(category, TestProducts.randomName(), 1000, TaxRate.STANDARD,
                StockStatus.LOW_STOCK_THRESHOLD, true);
        Product enough = testProducts.product(category, TestProducts.randomName(), 1000, TaxRate.STANDARD,
                StockStatus.LOW_STOCK_THRESHOLD + 1, true);

        mockMvc.perform(get("/products/{id}", soldOut.getId()))
                .andExpect(content().string(containsString("在庫切れ")));
        mockMvc.perform(get("/products/{id}", low.getId()))
                .andExpect(content().string(containsString("残りわずか")));
        mockMvc.perform(get("/products/{id}", enough.getId()))
                .andExpect(content().string(containsString("在庫あり")))
                .andExpect(content().string(not(containsString("在庫数"))));
    }

    // ---------------------------------------------------------------- 許可していない操作

    @Test
    void 商品を変更するURLは許可していない() throws Exception {
        Product product = testProducts.published(category, TestProducts.randomName());

        // 未ログインならログイン画面へ、会員でも拒否される（デフォルト拒否）
        mockMvc.perform(get("/products/{id}/edit", product.getId()))
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/products/{id}/edit", product.getId())
                        .with(user(TestUsers.principal(testUsers.active()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/products").with(csrf())
                        .with(user(TestUsers.principal(testUsers.active()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/products/{id}", product.getId()).with(csrf())
                        .with(user(TestUsers.principal(testUsers.active()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void トップページから商品一覧へ移動できる() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(content().string(containsString("href=\"/products\"")));
    }

    private void createPublished(int count) {
        for (int i = 0; i < count; i++) {
            testProducts.published(category, TestProducts.randomName());
        }
    }

    private Page<?> page(MockHttpServletRequestBuilder request) throws Exception {
        Object products = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("products");
        return (Page<?>) products;
    }
}
