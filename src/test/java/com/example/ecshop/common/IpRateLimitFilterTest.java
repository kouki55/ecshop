package com.example.ecshop.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecshop.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * IP 単位の回数制限の単体テスト（ログイン：1 分に 3 回、登録：1 時間に 2 回、再設定の依頼：1 時間に 2 回）。
 */
class IpRateLimitFilterTest {

    private final MutableClock clock = new MutableClock();
    private final IpRateLimitFilter filter = new IpRateLimitFilter(new RateLimitProperties(
            new RateLimitProperties.Limit(3, Duration.ofMinutes(1)),
            new RateLimitProperties.Limit(2, Duration.ofHours(1)),
            new RateLimitProperties.Limit(2, Duration.ofHours(1))), clock);

    @Test
    void 上限を超えたログインは429になる() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(send("POST", "/login", "192.0.2.1")).isEqualTo(200);
        }
        assertThat(send("POST", "/login", "192.0.2.1")).isEqualTo(429);
    }

    @Test
    void 時間が経てば再び受け付ける() throws Exception {
        for (int i = 0; i < 4; i++) {
            send("POST", "/login", "192.0.2.1");
        }
        clock.advance(Duration.ofSeconds(59));
        assertThat(send("POST", "/login", "192.0.2.1")).isEqualTo(429);

        clock.advance(Duration.ofSeconds(1));
        assertThat(send("POST", "/login", "192.0.2.1")).isEqualTo(200);
    }

    @Test
    void 別のIPアドレスは影響を受けない() throws Exception {
        for (int i = 0; i < 4; i++) {
            send("POST", "/login", "192.0.2.1");
        }
        assertThat(send("POST", "/login", "192.0.2.2")).isEqualTo(200);
    }

    @Test
    void XForwardedForヘッダーは信用しない() throws Exception {
        for (int i = 0; i < 3; i++) {
            send("POST", "/login", "192.0.2.1");
        }
        MockHttpServletRequest request = request("POST", "/login", "192.0.2.1");
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(send(request)).isEqualTo(429);
    }

    @Test
    void 会員登録はログインとは別に数える() throws Exception {
        for (int i = 0; i < 4; i++) {
            send("POST", "/login", "192.0.2.1");
        }
        assertThat(send("POST", "/register", "192.0.2.1")).isEqualTo(200);
        assertThat(send("POST", "/register", "192.0.2.1")).isEqualTo(200);
        assertThat(send("POST", "/register", "192.0.2.1")).isEqualTo(429);
    }

    @Test
    void パスワード再設定の依頼も上限を超えると429になる() throws Exception {
        assertThat(send("POST", "/password/forgot", "192.0.2.1")).isEqualTo(200);
        assertThat(send("POST", "/password/forgot", "192.0.2.1")).isEqualTo(200);
        assertThat(send("POST", "/password/forgot", "192.0.2.1")).isEqualTo(429);
        // 新しいパスワードの送信（/password/reset）はトークンが必要なので数えない
        assertThat(send("POST", "/password/reset", "192.0.2.1")).isEqualTo(200);
    }

    @Test
    void 画面の表示や対象外のURLは数えない() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(send("GET", "/login", "192.0.2.1")).isEqualTo(200);
            assertThat(send("POST", "/register/verify", "192.0.2.1")).isEqualTo(200);
        }
    }

    private int send(String method, String path, String remoteAddr) throws Exception {
        return send(request(method, path, remoteAddr));
    }

    private int send(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    private static MockHttpServletRequest request(String method, String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
