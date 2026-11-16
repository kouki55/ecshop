package com.example.ecshop.order;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * お届け予定日（注文日の 3〜5 日後。仮の値）。
 * 「注文日」は日本時間の日付で数える（UTC のままだと、日本の午前 0〜9 時の注文が前日扱いになるため）。
 */
public record DeliveryEstimate(LocalDate earliest, LocalDate latest) {

    public static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");
    public static final int MIN_DAYS = 3;
    public static final int MAX_DAYS = 5;

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy年M月d日(E)", Locale.JAPANESE);

    public static DeliveryEstimate from(Instant orderedAt) {
        LocalDate orderDate = orderedAt.atZone(JAPAN).toLocalDate();
        return new DeliveryEstimate(orderDate.plusDays(MIN_DAYS), orderDate.plusDays(MAX_DAYS));
    }

    /** 画面・メールに出す文字列（例：2026年11月7日(土)〜2026年11月9日(月)）。 */
    public String label() {
        return earliest.format(FORMAT) + "〜" + latest.format(FORMAT);
    }
}
