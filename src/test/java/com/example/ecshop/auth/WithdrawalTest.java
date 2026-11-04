package com.example.ecshop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.ecshop.CapturingMailSender;
import com.example.ecshop.IntegrationTest;
import com.example.ecshop.MutableClock;
import com.example.ecshop.TestUsers;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class WithdrawalTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestUsers testUsers;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private CapturingMailSender mailSender;
    @Autowired
    private MutableClock clock;

    private User user;

    @BeforeEach
    void setUp() {
        clock.reset();
        mailSender.clear();
        user = testUsers.active();
    }

    @Test
    void 退会した会員は元のメールアドレスとパスワードでログインできない() throws Exception {
        withdraw(user, TestUsers.PASSWORD).andExpect(redirectedUrl("/withdrawn"));

        login(new MockHttpSession(), user.getEmail(), TestUsers.PASSWORD)
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void 退会すると個人情報が置き換わり住所が削除される() throws Exception {
        testUsers.address(user, "自分花子");

        withdraw(user, TestUsers.PASSWORD).andExpect(redirectedUrl("/withdrawn"));

        // 行は残る（過去の注文が参照するため）が、個人情報は残らない
        User reloaded = testUsers.reload(user);
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(reloaded.getEmail()).isEqualTo("withdrawn-" + user.getId() + "@withdrawn.invalid");
        assertThat(reloaded.getName()).isEqualTo("退会済み会員");
        assertThat(reloaded.getPasswordHash()).isNotEqualTo(user.getPasswordHash());
        assertThat(addressRepository.countByUserId(user.getId())).isZero();
        assertThat(userRepository.findByEmailIgnoreCase(user.getEmail())).isEmpty();
    }

    @Test
    void 退会すると今のセッションはログアウトされる() throws Exception {
        MockHttpSession session = new MockHttpSession();
        login(session, user.getEmail(), TestUsers.PASSWORD).andExpect(authenticated());

        mockMvc.perform(post("/account/withdraw").session(session)
                        .param("password", TestUsers.PASSWORD).with(csrf()))
                .andExpect(redirectedUrl("/withdrawn"));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 退会するとほかのブラウザのセッションも失効する() throws Exception {
        MockHttpSession otherBrowser = new MockHttpSession();
        login(otherBrowser, user.getEmail(), TestUsers.PASSWORD).andExpect(authenticated());

        withdraw(user, TestUsers.PASSWORD).andExpect(redirectedUrl("/withdrawn"));

        mockMvc.perform(get("/account").session(otherBrowser))
                .andExpect(redirectedUrl("/login?expired"))
                .andExpect(unauthenticated());
    }

    @Test
    void 退会完了のメールが元のアドレスに届く() throws Exception {
        withdraw(user, TestUsers.PASSWORD);

        List<SimpleMailMessage> mails = mailSender.sentTo(user.getEmail());
        assertThat(mails).hasSize(1);
        assertThat(mails.get(0).getSubject()).contains("退会手続きが完了しました");
    }

    @Test
    void 退会後は同じメールアドレスで新しく会員登録できる() throws Exception {
        withdraw(user, TestUsers.PASSWORD);
        mailSender.clear();

        mockMvc.perform(post("/register")
                        .param("name", "再登録")
                        .param("email", user.getEmail())
                        .param("password", TestUsers.PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl("/register/sent"));

        User newUser = userRepository.findByEmailIgnoreCase(user.getEmail()).orElseThrow();
        assertThat(newUser.getId()).isNotEqualTo(user.getId());
        assertThat(newUser.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(mailSender.sentTo(user.getEmail()).get(0).getText()).contains("/register/verify?token=");
    }

    @Test
    void パスワードを間違えると退会できず失敗回数に数えられる() throws Exception {
        withdraw(user, "wrong-password-for-test")
                .andExpect(view().name("account/withdraw"))
                .andExpect(model().attributeHasFieldErrors("form", "password"));

        User reloaded = testUsers.reload(user);
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(1);
    }

    @Test
    void パスワードを5回間違えるとロックされ正しいパスワードでも退会できない() throws Exception {
        for (int i = 0; i < LoginAttemptService.MAX_FAILED_ATTEMPTS; i++) {
            withdraw(user, "wrong-password-for-test").andExpect(view().name("account/withdraw"));
        }

        withdraw(user, TestUsers.PASSWORD)
                .andExpect(view().name("account/withdraw"))
                .andExpect(content().string(containsString("しばらく時間をおいてから")));
        assertThat(testUsers.reload(user).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void パスワードが空なら退会できない() throws Exception {
        withdraw(user, "")
                .andExpect(view().name("account/withdraw"))
                .andExpect(model().attributeHasFieldErrors("form", "password"));
        assertThat(testUsers.reload(user).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 退会は自分自身にしか効かない() throws Exception {
        User other = testUsers.active();

        // フォームに他人の ID を紛れ込ませても、退会するのはログイン中の会員だけ
        mockMvc.perform(post("/account/withdraw")
                        .param("password", TestUsers.PASSWORD)
                        .param("userId", String.valueOf(other.getId()))
                        .param("id", String.valueOf(other.getId()))
                        .with(user(TestUsers.principal(user))).with(csrf()))
                .andExpect(redirectedUrl("/withdrawn"));

        assertThat(testUsers.reload(user).getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(testUsers.reload(other).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void CSRFトークンがない退会の送信は拒否される() throws Exception {
        mockMvc.perform(post("/account/withdraw")
                        .param("password", TestUsers.PASSWORD)
                        .with(user(TestUsers.principal(user))))
                .andExpect(status().isForbidden());

        assertThat(testUsers.reload(user).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    private ResultActions withdraw(User target, String password) throws Exception {
        return mockMvc.perform(post("/account/withdraw")
                .param("password", password)
                .with(user(TestUsers.principal(target))).with(csrf()));
    }

    private ResultActions login(MockHttpSession session, String email, String password) throws Exception {
        return mockMvc.perform(post("/login")
                .session(session)
                .param("email", email)
                .param("password", password)
                .with(csrf()));
    }
}
