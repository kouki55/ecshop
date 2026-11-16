package com.example.ecshop.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled}（決まった間隔で動く処理）を有効にする。今は未払い注文の自動キャンセルだけで使う。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
