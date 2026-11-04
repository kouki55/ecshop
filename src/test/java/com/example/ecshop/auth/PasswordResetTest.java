package com.example.ecshop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class PasswordResetTest {

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/password/reset\\?token=([A-Za-z0-9_-]+)");
    private static final String NEW_PASSWORD = "brake-pads-and-coolant-2026";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestUsers testUsers;
    @Autowired
    private CapturingMailSender mailSender;
    @Autowired
    private MutableClock clock;
    @Autowired
    private TokenGenerator tokenGenerator;
    @Autowired
    private UserTokenRepository tokenRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        clock.reset();
        mailSender.clear();
    }

    // ---------------------------------------------------------------- 再設定の依頼

    @Test
    void 有効な会員には再設定リンクのメールが届きトークンはハッシュだけが保存される() throws Exception {
        User user = testUsers.active();

        requestReset(user.getEmail()).andExpect(redirectedUrl("/password/forgot/sent"));

        List<SimpleMailMessage> mails = mailSender.sentTo(user.getEmail());
        assertThat(mails).hasSize(1);
        String token = tokenFrom(mails.get(0));
        List<String> stored = jdbc.queryForList(
                "SELECT token_hash FROM user_tokens WHERE user_id = ? AND purpose = 'PASSWORD_RESET'",
                String.class, user.getId());
        assertThat(stored).containsExactly(tokenGenerator.hash(token)).doesNotContain(token);
        // 依頼しただけではパスワードは変わらない
        assertThat(testUsers.reload(user).getPasswordHash()).isEqualTo(user.getPasswordHash());
    }

    @Test
    void 未登録や仮登録や退会済みのアドレスでも同じ画面になりメールは送られない() throws Exception {
        String unknown = TestUsers.randomEmail();
        User pending = testUsers.pending();
        User withdrawn = testUsers.withdrawn();

        for (String email : List.of(unknown, pending.getEmail(), withdrawn.getEmail())) {
            requestReset(email).andExpect(redirectedUrl("/password/forgot/sent"));
            assertThat(mailSender.sentTo(email)).isEmpty();
        }
        Integer tokens = jdbc.queryForObject(
                "SELECT count(*) FROM user_tokens WHERE purpose = 'PASSWORD_RESET' AND user_id IN (?, ?)",
                Integer.class, pending.getId(), withdrawn.getId());
        assertThat(tokens).isZero();
    }

    @Test
    void 完了画面は登録の有無を区別しない文面になっている() throws Exception {
        mockMvc.perform(get("/password/forgot/sent"))
                .andExpect(content().string(containsString("登録されていれば")));
    }

    @Test
    void メールアドレスの形式が違えば入力画面に戻る() throws Exception {
        requestReset("not-an-email")
                .andExpect(view().name("auth/password-forgot"))
                .andExpect(model().attributeHasFieldErrors("form", "email"));
    }

    // ---------------------------------------------------------------- リンクの有効性

    @Test
    void リンクを開いただけではトークンは使用済みにならない() throws Exception {
        User user = testUsers.active();
        String token = issueToken(user);

        mockMvc.perform(get("/password/reset").param("token", token))
                .andExpect(view().name("auth/password-reset"));
        mockMvc.perform(get("/password/reset").param("token", token))
                .andExpect(view().name("auth/password-reset"));

        reset(token, NEW_PASSWORD).andExpect(redirectedUrl("/login?reset"));
    }

    @Test
    void 有効期限の30分を過ぎたトークンは使えない() throws Exception {
        User user = testUsers.active();
        String token = issueToken(user);

        clock.advance(Duration.ofMinutes(30));

        mockMvc.perform(get("/password/reset").param("token", token))
                .andExpect(view().name("auth/password-reset-invalid"));
        reset(token, NEW_PASSWORD).andExpect(view().name("auth/password-reset-invalid"));
        assertThat(testUsers.reload(user).getPasswordHash()).isEqualTo(user.getPasswordHash());
    }

    @Test
    void 有効期限の直前ならトークンを使える() throws Exception {
        User user = testUsers.active();
        String token = issueToken(user);

        clock.advance(Duration.ofMinutes(30).minusSeconds(1));

        reset(token, NEW_PASSWORD).andExpect(redirectedUrl("/login?reset"));
    }

    @Test
    void 使用済みのトークンは使えない() throws Exception {
        User user = testUsers.active();
        String token = issueToken(user);
        reset(token, NEW_PASSWORD).andExpect(redirectedUrl("/login?reset"));
        String hashAfterFirstReset = testUsers.reload(user).getPasswordHash();

        mockMvc.perform(get("/password/reset").param("token", token))
                .andExpect(view().name("auth/password-reset-invalid"));
        reset(token, "another-new-password-here").andExpect(view().name("auth/password-reset-invalid"));

        assertThat(testUsers.reload(user).getPasswordHash()).isEqualTo(hashAfterFirstReset);
    }

    @Test
    void 新しく依頼すると古いリンクは使えなくなる() throws Exception {
        User user = testUsers.active();
        String first = issueToken(user);
        String second = issueToken(user);

        reset(first, NEW_PASSWORD).andExpect(view().name("auth/password-reset-invalid"));
        reset(second, NEW_PASSWORD).andExpect(redirectedUrl("/login?reset"));
    }

    @Test
    void 存在しないトークンや形式が違うトークンは使えない() throws Exception {
        reset(tokenGenerator.generate(), NEW_PASSWORD).andExpect(view().name("auth/password-reset-invalid"));
        reset("<script>", NEW_PASSWORD).andExpect(view().name("auth/password-reset-invalid"));
        reset("", NEW_PASSWORD).andExpect(view().name("auth/password-reset-invalid"));
        mockMvc.perform(get("/password/reset")).andExpect(view().name("auth/password-reset-invalid"));
    }

    @Test
    void メール確認用のトークンではパスワードを再設定できない() throws Exception {
        User user = testUsers.active();
        String token = tokenGenerator.generate();
        tokenRepository.save(UserToken.issue(user.getId(), TokenPurpose.EMAIL_VERIFICATION,
                tokenGenerator.hash(token), clock.instant(), clock.instant().plus(Duration.ofHours(1))));

        reset(token, NEW_PASSWORD).andExpect(view().name("auth/password-reset-invalid"));
        assertThat(testUsers.reload(user).getPasswordHash()).isEqualTo(user.getPasswordHash());
    }

    // ---------------------------------------------------------------- 新しいパスワード

    @Test
    void 新しいパスワードにも規則が適用され拒否してもトークンは使える() throws Exception {
        User user = testUsers.active();
        String token = issueToken(user);

        reset(token, "abcdefghijklmn")
                .andExpect(view().name("auth/password-reset"))
                .andExpect(model().attributeHasFieldErrors("form", "password"))
                .andExpect(content().string(containsString("パスワードは15文字以上で入力してください")));
        reset(token, "passwordpassword")
                .andExpect(content().string(containsString("漏えいしたパスワード")));
        reset(token, user.getEmail())
                .andExpect(content().string(containsString("メールアドレスと同じパスワードは使えません")));

        // 拒否されている間はトークンを消費しないので、同じリンクで設定し直せる
        reset(token, NEW_PASSWORD).andExpect(redirectedUrl("/login?reset"));
    }

    @Test
    void 変更後は古いパスワードでログインできず新しいパスワードでログインできる() throws Exception {
        User user = testUsers.active();
        reset(issueToken(user), NEW_PASSWORD).andExpect(redirectedUrl("/login?reset"));

        login(new MockHttpSession(), user.getEmail(), TestUsers.PASSWORD).andExpect(unauthenticated());
        login(new MockHttpSession(), user.getEmail(), NEW_PASSWORD).andExpect(authenticated());
    }

    @Test
    void 変更すると通知メールが届く() throws Exception {
        User user = testUsers.active();
        String token = issueToken(user);
        mailSender.clear();

        reset(token, NEW_PASSWORD);

        List<SimpleMailMessage> mails = mailSender.sentTo(user.getEmail());
        assertThat(mails).hasSize(1);
        assertThat(mails.get(0).getSubject()).contains("パスワードが変更されました");
    }

    @Test
    void ロック中の会員も再設定するとロックが解除される() throws Exception {
        User user = testUsers.locked(testUsers.active());
        assertThat(user.isLockedAt(clock.instant())).isTrue();

        reset(issueToken(user), NEW_PASSWORD).andExpect(redirectedUrl("/login?reset"));

        User reloaded = testUsers.reload(user);
        assertThat(reloaded.getLockedUntil()).isNull();
        assertThat(reloaded.getFailedLoginCount()).isZero();
        login(new MockHttpSession(), user.getEmail(), NEW_PASSWORD).andExpect(authenticated());
    }

    @Test
    void 変更するとほかのブラウザのセッションが失効する() throws Exception {
        User user = testUsers.active();
        MockHttpSession otherBrowser = new MockHttpSession();
        login(otherBrowser, user.getEmail(), TestUsers.PASSWORD).andExpect(authenticated());
        mockMvc.perform(get("/account").session(otherBrowser)).andExpect(status().isOk());

        reset(issueToken(user), NEW_PASSWORD).andExpect(redirectedUrl("/login?reset"));

        mockMvc.perform(get("/account").session(otherBrowser))
                .andExpect(redirectedUrl("/login?expired"))
                .andExpect(unauthenticated());
    }

    @Test
    void CSRFトークンがない送信は拒否される() throws Exception {
        User user = testUsers.active();
        String token = issueToken(user);

        mockMvc.perform(post("/password/forgot").param("email", user.getEmail()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/password/reset").param("token", token).param("password", NEW_PASSWORD))
                .andExpect(status().isForbidden());

        assertThat(testUsers.reload(user).getPasswordHash()).isEqualTo(user.getPasswordHash());
    }

    /** 再設定を依頼し、届いたメールからトークンを取り出す。 */
    private String issueToken(User user) throws Exception {
        requestReset(user.getEmail()).andExpect(redirectedUrl("/password/forgot/sent"));
        List<SimpleMailMessage> mails = mailSender.sentTo(user.getEmail());
        return tokenFrom(mails.get(mails.size() - 1));
    }

    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/password/forgot").param("email", email).with(csrf()));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mockMvc.perform(post("/password/reset")
                .param("token", token)
                .param("password", password)
                .with(csrf()));
    }

    private ResultActions login(MockHttpSession session, String email, String password) throws Exception {
        return mockMvc.perform(post("/login")
                .session(session)
                .param("email", email)
                .param("password", password)
                .with(csrf()));
    }

    private static String tokenFrom(SimpleMailMessage mail) {
        Matcher matcher = TOKEN_IN_LINK.matcher(mail.getText());
        assertThat(matcher.find()).as("メールに再設定リンクが含まれている").isTrue();
        return matcher.group(1);
    }
}
