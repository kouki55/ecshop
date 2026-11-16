package com.example.ecshop.order;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 注文まわりの設定（application.yml の app.order）。
 *
 * @param paymentTimeout   注文を受け付けてから支払いを待つ時間。過ぎたら自動でキャンセルし、在庫を戻す
 * @param autoCancelCron   自動キャンセルを動かす間隔（cron 式。"-" なら動かさない）
 */
@ConfigurationProperties("app.order")
public record OrderProperties(Duration paymentTimeout, String autoCancelCron) {
}
