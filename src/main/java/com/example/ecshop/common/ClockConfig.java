package com.example.ecshop.common;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 現在時刻の取得元。Instant.now() を直接呼ばずにこの Clock を使うことで、
 * テストでは時刻を自由に進められる（例：ロックが 15 分後に解除されるかの確認）。
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        // PostgreSQL の TIMESTAMPTZ はマイクロ秒までしか保存できない。ナノ秒の時刻を保存すると
        // 丸めで値がずれ、「ちょうど 15 分後」の判定が狂うことがあるので、最初からマイクロ秒単位にそろえる
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
