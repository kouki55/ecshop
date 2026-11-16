package com.example.ecshop.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支払われないまま期限（app.order.payment-timeout）を過ぎた注文を、定期的に自動でキャンセルする。
 * 確定の時点で在庫を減らしているので、放っておくと支払われない注文が在庫を押さえ続けてしまうため。
 * 動かす間隔は app.order.auto-cancel-cron（テストでは "-" にして止め、run() を直接呼ぶ）。
 */
@Component
public class UnpaidOrderCanceller {

    private static final Logger log = LoggerFactory.getLogger(UnpaidOrderCanceller.class);

    private final OrderService orderService;

    public UnpaidOrderCanceller(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(cron = "${app.order.auto-cancel-cron}", zone = "Asia/Tokyo")
    public void run() {
        int cancelled = orderService.cancelExpiredOrders();
        if (cancelled > 0) {
            log.info("支払い期限を過ぎた注文をキャンセルしました: {} 件", cancelled);
        }
    }
}
