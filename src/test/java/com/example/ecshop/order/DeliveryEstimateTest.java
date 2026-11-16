package com.example.ecshop.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** お届け予定日は、日本時間の注文日の 3〜5 日後。 */
class DeliveryEstimateTest {

    @Test
    void 日本時間の23時59分の注文はその日の注文として数える() {
        // UTC 14:59:59 = 日本時間 11 月 16 日 23:59:59
        DeliveryEstimate estimate = DeliveryEstimate.from(Instant.parse("2026-11-16T14:59:59Z"));
        assertThat(estimate.earliest()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(estimate.latest()).isEqualTo(LocalDate.of(2026, 11, 21));
    }

    @Test
    void 日本時間の0時の注文は翌日の注文として数える() {
        // UTC 15:00 = 日本時間 11 月 17 日 0:00（UTC の日付のままだと 16 日扱いになってしまう）
        DeliveryEstimate estimate = DeliveryEstimate.from(Instant.parse("2026-11-16T15:00:00Z"));
        assertThat(estimate.earliest()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(estimate.latest()).isEqualTo(LocalDate.of(2026, 11, 22));
    }

    @Test
    void 表示用の文字列は曜日付き() {
        DeliveryEstimate estimate = DeliveryEstimate.from(Instant.parse("2026-11-16T00:00:00Z"));
        assertThat(estimate.label()).isEqualTo("2026年11月19日(木)〜2026年11月21日(土)");
    }
}
