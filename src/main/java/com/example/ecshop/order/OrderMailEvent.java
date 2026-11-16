package com.example.ecshop.order;

/**
 * 注文の確認メールの依頼。支払いが成功したトランザクションの中で発行し、
 * コミット後に OrderMailSender が受け取って送る。
 * メール送信は別スレッドで動くので、DB を読み直さずに済むよう必要な値をすべて持たせる。
 */
public record OrderMailEvent(String email, String name, OrderView order) {

    @Override
    public String toString() {
        // メールアドレス・名前・住所をログに出さない
        return "OrderMailEvent[orderId=" + order.id() + "]";
    }
}
