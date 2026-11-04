package com.example.ecshop.common;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IP アドレス単位の回数制限の設定（application.yml の app.rate-limit）。
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(Limit login, Limit register, Limit passwordReset) {

    /** window の間に maxRequests 回まで受け付ける。 */
    public record Limit(int maxRequests, Duration window) {
    }
}
