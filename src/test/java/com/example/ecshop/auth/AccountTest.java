package com.example.ecshop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.ecshop.IntegrationTest;
import com.example.ecshop.MutableClock;
import com.example.ecshop.TestUsers;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 登録情報（氏名）と住所の閲覧・変更。特に「他人のデータにアクセスできない」ことを確認する。
 */
@IntegrationTest
class AccountTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestUsers testUsers;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private MutableClock clock;

    private User me;
    private User other;
    private Address othersAddress;

    @BeforeEach
    void setUp() {
        clock.reset();
        me = testUsers.active();
        other = testUsers.active();
        othersAddress = testUsers.address(other, "他人太郎");
    }

    // ---------------------------------------------------------------- 閲覧

    @Test
    void 登録情報には自分の情報と住所だけが表示される() throws Exception {
        testUsers.address(me, "自分花子");

        mockMvc.perform(get("/account").with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(me.getEmail())))
                .andExpect(content().string(containsString("自分花子")))
                .andExpect(content().string(not(containsString(other.getEmail()))))
                .andExpect(content().string(not(containsString("他人太郎"))));
    }

    @Test
    void 未ログインなら登録情報の画面はログイン画面へ送られる() throws Exception {
        mockMvc.perform(get("/account")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/account/addresses/" + othersAddress.getId() + "/edit"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 管理者ロールだけのアカウントは登録情報の画面を開けない() throws Exception {
        mockMvc.perform(get("/account").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- 氏名

    @Test
    void 氏名を変更できる() throws Exception {
        mockMvc.perform(post("/account/profile").param("name", "  新しい名前  ")
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(redirectedUrl("/account"));

        assertThat(testUsers.reload(me).getName()).isEqualTo("新しい名前");
    }

    @Test
    void 氏名の変更でほかの項目や他人の情報は変えられない() throws Exception {
        // フォームにない項目（会員 ID・メールアドレス・状態など）を紛れ込ませても無視される
        mockMvc.perform(post("/account/profile")
                        .param("name", "書き換え")
                        .param("id", String.valueOf(other.getId()))
                        .param("userId", String.valueOf(other.getId()))
                        .param("email", "attacker@example.com")
                        .param("role", "ADMIN")
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(redirectedUrl("/account"));

        User reloadedMe = testUsers.reload(me);
        assertThat(reloadedMe.getName()).isEqualTo("書き換え");
        assertThat(reloadedMe.getEmail()).isEqualTo(me.getEmail());
        assertThat(reloadedMe.getRole()).isEqualTo(UserRole.MEMBER);
        assertThat(testUsers.reload(other).getName()).isEqualTo(other.getName());
    }

    @Test
    void 氏名が空や長すぎる場合は拒否される() throws Exception {
        for (String name : new String[] {"", "   ", "あ".repeat(101)}) {
            mockMvc.perform(post("/account/profile").param("name", name)
                            .with(user(TestUsers.principal(me))).with(csrf()))
                    .andExpect(view().name("account/profile-edit"))
                    .andExpect(model().attributeHasFieldErrors("form", "name"));
        }
        assertThat(testUsers.reload(me).getName()).isEqualTo(me.getName());
    }

    // ---------------------------------------------------------------- 住所の追加・変更・削除

    @Test
    void 住所を追加でき郵便番号と電話番号はハイフンを除いて保存される() throws Exception {
        mockMvc.perform(withAddressParams(post("/account/addresses"),
                        "postalCode", "150-0001", "phone", "090-1234-5678")
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(redirectedUrl("/account"));

        Address saved = addressRepository.findByUserIdOrderByIdAsc(me.getId()).get(0);
        assertThat(saved.getPostalCode()).isEqualTo("1500001");
        assertThat(saved.getPhone()).isEqualTo("09012345678");
        assertThat(saved.getPrefecture()).isEqualTo("東京都");
        assertThat(saved.getAddressLine2()).isNull();
    }

    @Test
    void 住所の入力値は検証される() throws Exception {
        mockMvc.perform(post("/account/addresses")
                        .param("recipientName", "")
                        .param("postalCode", "12345")
                        .param("prefecture", "東京")
                        .param("city", "")
                        .param("addressLine1", "あ".repeat(201))
                        .param("phone", "03-1234")
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(view().name("account/address-form"))
                .andExpect(model().attributeHasFieldErrors("form",
                        "recipientName", "postalCode", "prefecture", "city", "addressLine1", "phone"));

        assertThat(addressRepository.countByUserId(me.getId())).isZero();
    }

    @Test
    void 住所は10件まで登録でき11件目は拒否される() throws Exception {
        for (int i = 0; i < AddressService.MAX_ADDRESSES; i++) {
            testUsers.address(me, "宛名" + i);
        }

        mockMvc.perform(withAddressParams(post("/account/addresses"))
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(view().name("account/address-form"))
                .andExpect(content().string(containsString("住所は10件まで登録できます")));

        assertThat(addressRepository.countByUserId(me.getId())).isEqualTo(AddressService.MAX_ADDRESSES);
    }

    @Test
    void 自分の住所は変更と削除ができる() throws Exception {
        Address mine = testUsers.address(me, "自分花子");

        mockMvc.perform(get("/account/addresses/" + mine.getId() + "/edit").with(user(TestUsers.principal(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("自分花子")));
        mockMvc.perform(withAddressParams(post("/account/addresses/" + mine.getId()), "recipientName", "変更後")
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(redirectedUrl("/account"));
        assertThat(addressRepository.findById(mine.getId()).orElseThrow().getRecipientName()).isEqualTo("変更後");

        mockMvc.perform(post("/account/addresses/" + mine.getId() + "/delete")
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(redirectedUrl("/account"));
        assertThat(addressRepository.findById(mine.getId())).isEmpty();
    }

    // ---------------------------------------------------------------- 他人の住所（URL の ID の書き換え）

    @Test
    void 他人の住所のIDをURLに入れても表示できない() throws Exception {
        mockMvc.perform(get("/account/addresses/" + othersAddress.getId() + "/edit")
                        .with(user(TestUsers.principal(me))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("他人太郎"))));
    }

    @Test
    void 他人の住所のIDをURLに入れても変更できない() throws Exception {
        mockMvc.perform(withAddressParams(post("/account/addresses/" + othersAddress.getId()),
                        "recipientName", "乗っ取り")
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(status().isNotFound());

        // 入力エラーのある送信でも、持ち主の確認が先に行われ 404 になる
        mockMvc.perform(post("/account/addresses/" + othersAddress.getId())
                        .param("recipientName", "")
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(status().isNotFound());

        Address unchanged = addressRepository.findById(othersAddress.getId()).orElseThrow();
        assertThat(unchanged.getRecipientName()).isEqualTo("他人太郎");
        assertThat(unchanged.getUserId()).isEqualTo(other.getId());
    }

    @Test
    void 他人の住所のIDをURLに入れても削除できない() throws Exception {
        mockMvc.perform(post("/account/addresses/" + othersAddress.getId() + "/delete")
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(addressRepository.findById(othersAddress.getId())).isPresent();
    }

    @Test
    void 住所の追加でuserIdを紛れ込ませても他人の住所にはならない() throws Exception {
        mockMvc.perform(withAddressParams(post("/account/addresses"),
                        "userId", String.valueOf(other.getId()), "id", String.valueOf(othersAddress.getId()))
                        .with(user(TestUsers.principal(me))).with(csrf()))
                .andExpect(redirectedUrl("/account"));

        assertThat(addressRepository.countByUserId(me.getId())).isEqualTo(1);
        assertThat(addressRepository.countByUserId(other.getId())).isEqualTo(1);
        assertThat(addressRepository.findById(othersAddress.getId()).orElseThrow().getRecipientName())
                .isEqualTo("他人太郎");
    }

    @Test
    void 存在しない住所のIDは他人の住所と同じ404になる() throws Exception {
        mockMvc.perform(get("/account/addresses/999999999/edit").with(user(TestUsers.principal(me))))
                .andExpect(status().isNotFound());
    }

    @Test
    void 住所のIDが数字でなければ400になる() throws Exception {
        mockMvc.perform(get("/account/addresses/abc/edit").with(user(TestUsers.principal(me))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    // ---------------------------------------------------------------- CSRF

    @Test
    void CSRFトークンがない送信は拒否される() throws Exception {
        Address mine = testUsers.address(me, "自分花子");

        mockMvc.perform(post("/account/profile").param("name", "x").with(user(TestUsers.principal(me))))
                .andExpect(status().isForbidden());
        mockMvc.perform(withAddressParams(post("/account/addresses")).with(user(TestUsers.principal(me))))
                .andExpect(status().isForbidden());
        mockMvc.perform(withAddressParams(post("/account/addresses/" + mine.getId()))
                        .with(user(TestUsers.principal(me))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/account/addresses/" + mine.getId() + "/delete").with(user(TestUsers.principal(me))))
                .andExpect(status().isForbidden());

        assertThat(testUsers.reload(me).getName()).isEqualTo(me.getName());
        assertThat(addressRepository.countByUserId(me.getId())).isEqualTo(1);
    }

    /**
     * 正しい住所の入力値を付ける。overrides に「名前, 値, 名前, 値 ...」を渡すと、その項目だけ差し替える
     * （param() は同じ名前で呼ぶと値が追加されてしまうので、先に Map で組み立てる）。
     */
    private static MockHttpServletRequestBuilder withAddressParams(MockHttpServletRequestBuilder builder,
                                                                   String... overrides) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("recipientName", "山田太郎");
        params.put("postalCode", "1000001");
        params.put("prefecture", "東京都");
        params.put("city", "千代田区");
        params.put("addressLine1", "千代田1-1");
        params.put("addressLine2", "");
        params.put("phone", "0312345678");
        for (int i = 0; i < overrides.length; i += 2) {
            params.put(overrides[i], overrides[i + 1]);
        }
        params.forEach(builder::param);
        return builder;
    }
}
