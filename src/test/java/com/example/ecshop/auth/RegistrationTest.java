package com.example.ecshop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class RegistrationTest {

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/register/verify\\?token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserTokenRepository tokenRepository;
    @Autowired
    private TokenGenerator tokenGenerator;
    @Autowired
    private CapturingMailSender mailSender;
    @Autowired
    private MutableClock clock;
    @Autowired
    private TestUsers testUsers;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        clock.reset();
        mailSender.clear();
    }

    // ---------------------------------------------------------------- 仮登録

    @Test
    void 登録すると仮登録の状態で保存され確認メールが送られる() throws Exception {
        String email = TestUsers.randomEmail();

        register("山田太郎", email, TestUsers.PASSWORD).andExpect(redirectedUrl("/register/sent"));

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(user.getName()).isEqualTo("山田太郎");
        // 平文ではなく bcrypt のハッシュで保存されている
        assertThat(user.getPasswordHash()).startsWith("$2a$").isNotEqualTo(TestUsers.PASSWORD);

        List<SimpleMailMessage> mails = mailSender.sentTo(email);
        assertThat(mails).hasSize(1);
        String token = tokenFrom(mails.get(0));
        // DB に保存されているのはトークンのハッシュで、メールのトークンそのものではない
        List<String> storedHashes = jdbc.queryForList(
                "SELECT token_hash FROM user_tokens WHERE user_id = ?", String.class, user.getId());
        assertThat(storedHashes).containsExactly(tokenGenerator.hash(token)).doesNotContain(token);
    }

    @Test
    void 文字数14のパスワードは拒否される() throws Exception {
        String email = TestUsers.randomEmail();

        register("山田太郎", email, "abcdefghijklmn")
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("form", "password"))
                .andExpect(content().string(containsString("パスワードは15文字以上で入力してください")));

        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
        assertThat(mailSender.sentTo(email)).isEmpty();
    }

    @Test
    void 漏えい済みのパスワードは拒否される() throws Exception {
        String email = TestUsers.randomEmail();

        register("山田太郎", email, "passwordpassword")
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("form", "password"));

        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void 入力エラーの画面にはパスワードを書き戻さない() throws Exception {
        register("", "not-an-email", TestUsers.PASSWORD)
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("form", "name", "email"))
                .andExpect(content().string(not(containsString(TestUsers.PASSWORD))));
    }

    @Test
    void 登録済みのアドレスでも画面は同じで会員は増えず通知メールが送られる() throws Exception {
        User existing = testUsers.active();
        String upperCaseEmail = existing.getEmail().toUpperCase(Locale.ROOT);

        register("別の人", upperCaseEmail, "another-password-for-test")
                .andExpect(redirectedUrl("/register/sent"));

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE lower(email) = lower(?)", Integer.class, existing.getEmail());
        assertThat(count).isEqualTo(1);
        // パスワードも名前も変わらない
        User reloaded = testUsers.reload(existing);
        assertThat(reloaded.getPasswordHash()).isEqualTo(existing.getPasswordHash());
        assertThat(reloaded.getName()).isEqualTo(existing.getName());
        // 確認リンクではなく「登録済み」の通知が届く
        List<SimpleMailMessage> mails = mailSender.sentTo(existing.getEmail());
        assertThat(mails).hasSize(1);
        assertThat(mails.get(0).getText()).contains("すでに ecshop に登録されています").doesNotContain("token=");
    }

    @Test
    void 仮登録のまま再登録すると古いリンクは無効になり新しいリンクで有効化できる() throws Exception {
        String email = TestUsers.randomEmail();
        register("最初の人", email, "first-password-for-test");
        register("山田太郎", email, TestUsers.PASSWORD);
        List<SimpleMailMessage> mails = mailSender.sentTo(email);
        assertThat(mails).hasSize(2);

        verify(tokenFrom(mails.get(0))).andExpect(view().name("auth/verify-invalid"));
        verify(tokenFrom(mails.get(1))).andExpect(view().name("auth/verify-done"));

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getName()).isEqualTo("山田太郎");
    }

    // ---------------------------------------------------------------- メール確認

    @Test
    void リンクを開いただけでは有効化されずボタンを押すと有効化される() throws Exception {
        String email = TestUsers.randomEmail();
        register("山田太郎", email, TestUsers.PASSWORD);
        String token = tokenFrom(mailSender.sentTo(email).get(0));

        mockMvc.perform(get("/register/verify").param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/verify-confirm"))
                // URL のトークンがほかのサイトへ Referer で漏れない
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getStatus())
                .isEqualTo(UserStatus.PENDING);

        verify(token).andExpect(view().name("auth/verify-done"));
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 一度使ったリンクは二度と使えない() throws Exception {
        String email = TestUsers.randomEmail();
        register("山田太郎", email, TestUsers.PASSWORD);
        String token = tokenFrom(mailSender.sentTo(email).get(0));

        verify(token).andExpect(view().name("auth/verify-done"));
        verify(token).andExpect(view().name("auth/verify-invalid"));
    }

    @Test
    void 有効期限の24時間を過ぎたリンクは使えない() throws Exception {
        String email = TestUsers.randomEmail();
        register("山田太郎", email, TestUsers.PASSWORD);
        String token = tokenFrom(mailSender.sentTo(email).get(0));

        clock.advance(Duration.ofHours(24));

        verify(token).andExpect(view().name("auth/verify-invalid"));
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getStatus())
                .isEqualTo(UserStatus.PENDING);
    }

    @Test
    void 有効期限の直前ならリンクを使える() throws Exception {
        String email = TestUsers.randomEmail();
        register("山田太郎", email, TestUsers.PASSWORD);
        String token = tokenFrom(mailSender.sentTo(email).get(0));

        clock.advance(Duration.ofHours(24).minusSeconds(1));

        verify(token).andExpect(view().name("auth/verify-done"));
    }

    @Test
    void 存在しないトークンや形式が違うトークンは無効() throws Exception {
        verify(tokenGenerator.generate()).andExpect(view().name("auth/verify-invalid"));
        verify("<script>alert(1)</script>").andExpect(view().name("auth/verify-invalid"));
        verify("").andExpect(view().name("auth/verify-invalid"));
        mockMvc.perform(get("/register/verify").param("token", "a/b"))
                .andExpect(view().name("auth/verify-invalid"));
        mockMvc.perform(get("/register/verify")).andExpect(view().name("auth/verify-invalid"));
    }

    @Test
    void パスワード再設定用のトークンでは有効化できない() throws Exception {
        User user = testUsers.pending();
        String token = tokenGenerator.generate();
        tokenRepository.save(UserToken.issue(user.getId(), TokenPurpose.PASSWORD_RESET,
                tokenGenerator.hash(token), clock.instant(), clock.instant().plus(Duration.ofHours(1))));

        verify(token).andExpect(view().name("auth/verify-invalid"));
        assertThat(testUsers.reload(user).getStatus()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    void トークンで有効になるのはそのトークンの会員だけ() throws Exception {
        String emailA = TestUsers.randomEmail();
        String emailB = TestUsers.randomEmail();
        register("会員A", emailA, TestUsers.PASSWORD);
        register("会員B", emailB, TestUsers.PASSWORD);

        verify(tokenFrom(mailSender.sentTo(emailA).get(0))).andExpect(view().name("auth/verify-done"));

        assertThat(userRepository.findByEmailIgnoreCase(emailA).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(userRepository.findByEmailIgnoreCase(emailB).orElseThrow().getStatus())
                .isEqualTo(UserStatus.PENDING);
    }

    private ResultActions register(String name, String email, String password) throws Exception {
        return mockMvc.perform(post("/register")
                .param("name", name)
                .param("email", email)
                .param("password", password)
                .with(csrf()));
    }

    private ResultActions verify(String token) throws Exception {
        return mockMvc.perform(post("/register/verify").param("token", token).with(csrf()));
    }

    private static String tokenFrom(SimpleMailMessage mail) {
        Matcher matcher = TOKEN_IN_LINK.matcher(mail.getText());
        assertThat(matcher.find()).as("メールに確認リンクが含まれている").isTrue();
        return matcher.group(1);
    }
}
