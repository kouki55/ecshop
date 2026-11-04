package com.example.ecshop.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ログイン・会員登録・パスワード再設定の依頼の POST を、接続元 IP アドレスごとに回数制限する（固定ウィンドウ方式）。
 *
 * <p>注意点：
 * <ul>
 *   <li>X-Forwarded-For ヘッダーは利用者が自由に書き換えられるので使わず、getRemoteAddr() だけを見る。</li>
 *   <li>回数はこのアプリのメモリ上にあるので、再起動で消え、サーバーが複数台だと台ごとに数える。
 *       本番ではリバースプロキシ（nginx の limit_req など）で行うべきもので、これはローカル用の簡易版。</li>
 *   <li>ローカルでは接続元がすべて 127.0.0.1 なので、実質的に「全体の上限」として働く。</li>
 * </ul>
 */
public class IpRateLimitFilter extends OncePerRequestFilter {

    /** 記録がこの件数を超えたら、期限切れの記録を掃除する（大量の IP からの攻撃でメモリを使い切らないため）。 */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final List<Rule> rules;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public IpRateLimitFilter(RateLimitProperties properties, Clock clock) {
        this.rules = List.of(
                new Rule("login", PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login"),
                        properties.login()),
                new Rule("register", PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/register"),
                        properties.register()),
                // 再設定メールを大量に送らせる攻撃を防ぐ
                new Rule("password-reset",
                        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/password/forgot"),
                        properties.passwordReset()));
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = rules.stream().filter(r -> r.matcher().matches(request)).findFirst().orElse(null);
        if (rule != null && !tryAcquire(rule, request.getRemoteAddr())) {
            // 429 Too Many Requests。画面は /error（汎用のエラー画面）が表示される
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean tryAcquire(Rule rule, String remoteAddr) {
        Instant now = clock.instant();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> w.isExpired(now));
        }
        // compute はキーごとに排他されるので、同時に来たリクエストでも数え漏れが起きない
        Window window = windows.compute(rule.name() + "|" + remoteAddr, (key, current) ->
                current == null || current.isExpired(now)
                        ? new Window(now.plus(rule.limit().window()), 1)
                        : new Window(current.endsAt(), current.count() + 1));
        return window.count() <= rule.limit().maxRequests();
    }

    private record Rule(String name, RequestMatcher matcher, RateLimitProperties.Limit limit) {
    }

    /** endsAt まで有効な 1 つの数え枠と、その枠の中での回数。 */
    private record Window(Instant endsAt, int count) {
        boolean isExpired(Instant now) {
            return !now.isBefore(endsAt);
        }
    }
}
