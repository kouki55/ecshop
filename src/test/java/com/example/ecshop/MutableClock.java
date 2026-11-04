package com.example.ecshop;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * テスト用の時計。advance() で時刻を進められる（「15 分後にロックが解除される」などの確認用）。
 * 本物の Clock（ClockConfig）と同じく、DB の精度に合わせてマイクロ秒単位にする。
 */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> now = new AtomicReference<>(currentInstant());

    /** 現在の実時刻に戻す。各テストの最初に呼ぶ。 */
    public void reset() {
        now.set(currentInstant());
    }

    private static Instant currentInstant() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
