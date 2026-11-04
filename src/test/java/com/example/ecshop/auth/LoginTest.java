package com.example.ecshop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.example.ecshop.IntegrationTest;
import com.example.ecshop.MutableClock;
import com.example.ecshop.TestUsers;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class LoginTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestUsers testUsers;
    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock.reset();
    }

    @Test
    void 有効な会員はログインできトップにメールアドレスが表示される() throws Exception {
        User user = testUsers.active();
        MockHttpSession session = new MockHttpSession();

        login(session, user.getEmail(), TestUsers.PASSWORD)
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(user.getEmail()).withRoles("MEMBER"));

        mockMvc.perform(get("/").session(session))
                .andExpect(content().string(containsString(user.getEmail())));
    }

    @Test
    void 仮登録のままではログインできない() throws Exception {
        User user = testUsers.pending();

        login(new MockHttpSession(), user.getEmail(), TestUsers.PASSWORD)
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void 仮登録のままでのログイン失敗はロックの回数に数えない() throws Exception {
        User user = testUsers.pending();

        login(new MockHttpSession(), user.getEmail(), "wrong-password-for-test");

        assertThat(testUsers.reload(user).getFailedLoginCount()).isZero();
    }

    @Test
    void 退会済みの会員はログインできない() throws Exception {
        User user = testUsers.withdrawn();

        login(new MockHttpSession(), user.getEmail(), TestUsers.PASSWORD)
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void 未登録のアドレスとパスワード違いは同じ失敗画面になる() throws Exception {
        User user = testUsers.active();

        login(new MockHttpSession(), TestUsers.randomEmail(), TestUsers.PASSWORD)
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
        login(new MockHttpSession(), user.getEmail(), "wrong-password-for-test")
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void ログイン失敗の画面には理由を区別しない汎用メッセージだけを出す() throws Exception {
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(containsString("メールアドレスとパスワードをご確認ください")));
    }

    @Test
    void メールアドレスの大文字小文字が違ってもログインできる() throws Exception {
        User user = testUsers.active();

        login(new MockHttpSession(), user.getEmail().toUpperCase(Locale.ROOT), TestUsers.PASSWORD)
                .andExpect(authenticated());
    }

    @Test
    void 長すぎるパスワードでログインしてもエラー画面にならず失敗扱いになる() throws Exception {
        User user = testUsers.active();

        // bcrypt の上限 72 バイトを超えるパスワード
        login(new MockHttpSession(), user.getEmail(), "あ".repeat(100))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void ログイン前後でセッションIDが変わる() throws Exception {
        User user = testUsers.active();
        MockHttpSession session = new MockHttpSession();
        String before = session.getId();

        // ブラウザと同じく、ログイン前のセッション ID を Cookie で送ってきた状態にする。
        // Spring Security は「送られてきたセッション ID が有効」なときに ID を作り直すため、
        // これがないと（spring-test 7.0.10 以降のモックでは）作り直しの処理自体が動かない
        mockMvc.perform(post("/login")
                        .session(session)
                        .with(request -> {
                            request.setRequestedSessionId(before);
                            return request;
                        })
                        .param("email", user.getEmail())
                        .param("password", TestUsers.PASSWORD)
                        .with(csrf()))
                .andExpect(authenticated());

        // セッション固定攻撃の対策：ログイン前の ID を攻撃者が知っていても、ログイン後のセッションは乗っ取れない
        assertThat(session.getId()).isNotEqualTo(before);
    }

    @Test
    void ログアウトするとセッションが破棄される() throws Exception {
        User user = testUsers.active();
        MockHttpSession session = new MockHttpSession();
        login(session, user.getEmail(), TestUsers.PASSWORD);

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());

        assertThat(session.isInvalid()).isTrue();
    }

    private ResultActions login(MockHttpSession session, String email, String password) throws Exception {
        return mockMvc.perform(post("/login")
                .session(session)
                .param("email", email)
                .param("password", password)
                .with(csrf()));
    }
}
