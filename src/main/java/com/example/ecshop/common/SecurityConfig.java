package com.example.ecshop.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.SessionLimit;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * アクセス制御の設定。許可する URL を明示的に列挙し、それ以外はすべて拒否する。
 *
 * <p>Spring Security の標準動作のうち、次のものは無効にせずそのまま使っている：
 * <ul>
 *   <li>CSRF 対策（POST にはトークンが必須。Thymeleaf の th:action を使うと自動で埋め込まれる）</li>
 *   <li>ログイン成功時のセッション ID の作り直し（セッション固定攻撃の対策）</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RateLimitProperties rateLimitProperties,
                                            Clock clock, SessionRegistry sessionRegistry) {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/", "/login", "/register", "/register/sent", "/register/verify",
                                "/password/forgot", "/password/forgot/sent", "/password/reset",
                                "/withdrawn",
                                // 商品の一覧と詳細は未ログインでも見られる。/products/* は 1 階層だけなので、
                                // /products/1/edit のような URL は下の denyAll で拒否される
                                "/products", "/products/*").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/register", "/register/verify", "/password/forgot", "/password/reset").permitAll()
                        // POST /login と POST /logout は、ここに来る前に Spring Security のフィルタが処理するので列挙不要
                        .requestMatchers("/error").permitAll()
                        // 登録情報・住所・退会は会員だけ。管理者アカウントは管理画面専用なので含めない
                        .requestMatchers("/account", "/account/**").hasRole("MEMBER")
                        // カートも会員だけ。どのカートかは常にログイン中の会員から決める
                        .requestMatchers("/cart", "/cart/**").hasRole("MEMBER")
                        .anyRequest().denyAll())
                // ログイン中のセッションを SessionRegistry に記録する（上限なし）。
                // パスワード変更・退会で失効させたセッションで操作すると、ログイン画面に送られる
                .sessionManagement(session -> session.sessionConcurrency(concurrency -> concurrency
                        .maximumSessions(SessionLimit.UNLIMITED)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/login?expired")))
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/")
                        // 失敗の理由（パスワード違い・仮登録・ロック中など）は区別せず、同じ画面に戻す
                        .failureUrl("/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .deleteCookies("JSESSIONID"))
                // メール確認リンクの URL にはトークンが含まれる。ほかのサイトへ Referer で送られないようにする
                .headers(headers -> headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                // Bean にすると Spring Boot がサーブレットフィルタとしても登録して 2 回動くので、ここで new する
                .addFilterBefore(new IpRateLimitFilter(rateLimitProperties, clock),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
