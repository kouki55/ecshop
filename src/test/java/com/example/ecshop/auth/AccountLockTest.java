package com.example.ecshop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.example.ecshop.IntegrationTest;
import com.example.ecshop.MutableClock;
import com.example.ecshop.TestUsers;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 5 回連続でログインに失敗したら 15 分ロックされることの確認。時刻は MutableClock で進める。
 */
@IntegrationTest
class AccountLockTest {

    private static final String WRONG_PASSWORD = "wrong-password-for-test";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestUsers testUsers;
    @Autowired
    private MutableClock clock;

    private User user;

    @BeforeEach
    void setUp() {
        clock.reset();
        user = testUsers.active();
    }

    @Test
    void 失敗4回の後に成功すると失敗回数が0に戻る() throws Exception {
        failTimes(4);
        assertThat(testUsers.reload(user).getFailedLoginCount()).isEqualTo(4);

        login(TestUsers.PASSWORD).andExpect(authenticated());

        assertThat(testUsers.reload(user).getFailedLoginCount()).isZero();
        // 数え直しになったので、さらに 4 回失敗してもロックされない
        failTimes(4);
        login(TestUsers.PASSWORD).andExpect(authenticated());
    }

    @Test
    void 失敗5回でロックされ正しいパスワードでもログインできない() throws Exception {
        failTimes(5);

        assertThat(testUsers.reload(user).getLockedUntil())
                .isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
        login(TestUsers.PASSWORD)
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void ロックは15分後に解除される() throws Exception {
        failTimes(5);

        clock.advance(Duration.ofMinutes(15).minusSeconds(1));
        login(TestUsers.PASSWORD).andExpect(unauthenticated());

        clock.advance(Duration.ofSeconds(1));
        login(TestUsers.PASSWORD).andExpect(authenticated());
        User reloaded = testUsers.reload(user);
        assertThat(reloaded.getFailedLoginCount()).isZero();
        assertThat(reloaded.getLockedUntil()).isNull();
    }

    @Test
    void ロック解除後の失敗は0から数え直す() throws Exception {
        failTimes(5);
        clock.advance(Duration.ofMinutes(15));

        login(WRONG_PASSWORD);

        User reloaded = testUsers.reload(user);
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(1);
        assertThat(reloaded.getLockedUntil()).isNull();
        login(TestUsers.PASSWORD).andExpect(authenticated());
    }

    @Test
    void ロック中の試行は回数に入らずロック期間も延びない() throws Exception {
        failTimes(5);
        Instant lockedUntil = testUsers.reload(user).getLockedUntil();

        clock.advance(Duration.ofMinutes(10));
        failTimes(3);

        User reloaded = testUsers.reload(user);
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(5);
        assertThat(reloaded.getLockedUntil()).isEqualTo(lockedUntil);
    }

    @Test
    void ロックされるのは失敗したアカウントだけ() throws Exception {
        User other = testUsers.active();
        failTimes(5);

        mockMvc.perform(post("/login")
                        .param("email", other.getEmail())
                        .param("password", TestUsers.PASSWORD)
                        .with(csrf()))
                .andExpect(authenticated());
    }

    private void failTimes(int times) throws Exception {
        for (int i = 0; i < times; i++) {
            login(WRONG_PASSWORD).andExpect(redirectedUrl("/login?error"));
        }
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/login")
                .param("email", user.getEmail())
                .param("password", password)
                .with(csrf()));
    }
}
