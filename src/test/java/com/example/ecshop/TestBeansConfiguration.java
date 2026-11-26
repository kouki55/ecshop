package com.example.ecshop;

import com.example.ecshop.auth.AddressRepository;
import com.example.ecshop.auth.UserRepository;
import com.example.ecshop.product.CategoryRepository;
import com.example.ecshop.product.ProductRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 結合テストで本物の代わりに使う Bean。@Primary なので、同じ型の本物より優先して注入される。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestBeansConfiguration {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    CapturingMailSender capturingMailSender() {
        return new CapturingMailSender();
    }

    /** 返金の失敗を再現できる決済。疑似決済の代わりに注入される。 */
    @Bean
    @Primary
    ControllablePaymentGateway controllablePaymentGateway() {
        return new ControllablePaymentGateway();
    }

    /**
     * @Async の処理（メール送信）を、テストでは呼び出したスレッドでそのまま実行する。
     * こうするとリクエストが終わった時点でメールが記録済みになり、待ち合わせ無しで確認できる。
     */
    @Bean
    @Primary
    TaskExecutor syncTaskExecutor() {
        return new SyncTaskExecutor();
    }

    @Bean
    TestUsers testUsers(UserRepository userRepository, AddressRepository addressRepository,
                        PasswordEncoder passwordEncoder, JdbcTemplate jdbc, MutableClock clock) {
        return new TestUsers(userRepository, addressRepository, passwordEncoder, jdbc, clock);
    }

    @Bean
    TestProducts testProducts(CategoryRepository categoryRepository, ProductRepository productRepository,
                              MutableClock clock) {
        return new TestProducts(categoryRepository, productRepository, clock);
    }
}
