package com.example.ecshop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * 結合テスト用の注釈。全テストクラスで設定をそろえることで、Spring は起動済みのコンテキスト
 * （と PostgreSQL のコンテナ）を使い回すので、テストが速くなる。
 * IP 単位の回数制限は、多数のリクエストを送るテストで引っかからないよう上限を大きくしている
 * （回数制限そのものは IpRateLimitFilterTest などで確認する）。
 * 未払い注文の自動キャンセルは、テストの途中で勝手に動かないよう止めている（OrderTest で直接呼んで確認する）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "app.rate-limit.login.max-requests=100000",
        "app.rate-limit.register.max-requests=100000",
        "app.rate-limit.password-reset.max-requests=100000",
        "app.order.auto-cancel-cron=-"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestBeansConfiguration.class})
public @interface IntegrationTest {
}
