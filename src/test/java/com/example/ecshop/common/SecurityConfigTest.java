package com.example.ecshop.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecshop.IntegrationTest;
import com.example.ecshop.TestUsers;
import com.example.ecshop.auth.User;
import com.example.ecshop.auth.UserRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestUsers testUsers;
    @Autowired
    private UserRepository userRepository;

    @Value("${server.servlet.session.timeout}")
    private Duration sessionTimeout;

    @Test
    void トップページは誰でも見られる() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ecshop")));
    }

    @Test
    void ログインと会員登録の画面は誰でも見られる() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/register")).andExpect(status().isOk());
        mockMvc.perform(get("/register/sent")).andExpect(status().isOk());
        mockMvc.perform(get("/password/forgot")).andExpect(status().isOk());
        mockMvc.perform(get("/password/forgot/sent")).andExpect(status().isOk());
        mockMvc.perform(get("/withdrawn")).andExpect(status().isOk());
    }

    @Test
    void 許可していないURLは未ログインならログイン画面へ送られる() throws Exception {
        mockMvc.perform(get("/foo")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/admin")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 許可していないURLはログインしていても拒否される() throws Exception {
        mockMvc.perform(get("/foo").with(user("member@example.com"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void エラー画面に内部情報を出さない() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("trace"))));
    }

    @Test
    void 無操作のセッションは30分で失効する設定になっている() {
        assertThat(sessionTimeout).isEqualTo(Duration.ofMinutes(30));
    }

    // ---------------------------------------------------------------- CSRF

    @Test
    void CSRFトークンがない会員登録の送信は拒否される() throws Exception {
        String email = TestUsers.randomEmail();

        mockMvc.perform(post("/register")
                        .param("name", "山田太郎")
                        .param("email", email)
                        .param("password", TestUsers.PASSWORD))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void CSRFトークンが正しくない会員登録の送信は拒否される() throws Exception {
        mockMvc.perform(post("/register")
                        .param("name", "山田太郎")
                        .param("email", TestUsers.randomEmail())
                        .param("password", TestUsers.PASSWORD)
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void CSRFトークンがないメール確認の送信は拒否される() throws Exception {
        mockMvc.perform(post("/register/verify").param("token", "abc"))
                .andExpect(status().isForbidden());
    }

    @Test
    void CSRFトークンがないログインの送信は拒否される() throws Exception {
        User user = testUsers.active();

        mockMvc.perform(post("/login")
                        .param("email", user.getEmail())
                        .param("password", TestUsers.PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }

    @Test
    void CSRFトークンがないログアウトの送信は拒否される() throws Exception {
        mockMvc.perform(post("/logout").with(user("member@example.com")))
                .andExpect(status().isForbidden());
    }
}
