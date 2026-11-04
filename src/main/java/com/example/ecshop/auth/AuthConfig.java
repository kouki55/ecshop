package com.example.ecshop.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthConfig {

    /** パスワードのハッシュ化。Spring Security のログイン処理もこの Bean を使って照合する。 */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CompromisedPasswordChecker compromisedPasswordChecker(AuthProperties properties) {
        return LocalCompromisedPasswordChecker.fromGzipResource(properties.compromisedPasswordList());
    }
}
