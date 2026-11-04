package com.example.ecshop.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @Async を有効にする。メール送信を別スレッドで行い、画面の応答時間がメール送信に左右されないようにする
 * （登録済みのアドレスのときだけ応答が遅い、という差から登録の有無が分からないようにするため）。
 * スレッドプールは Spring Boot が用意するもの（applicationTaskExecutor）を使う。
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
