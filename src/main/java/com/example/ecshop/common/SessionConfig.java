package com.example.ecshop.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * ログイン中のセッションを会員ごとに記録する仕組み（Spring Security 標準）。
 * パスワード変更や退会のときに、その会員がほかのブラウザで開いているセッションも失効させるために使う。
 */
@Configuration
public class SessionConfig {

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** セッションが破棄されたことを SessionRegistry に伝え、記録が残り続けないようにする。 */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
