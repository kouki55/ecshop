package com.example.ecshop.payment;

import java.util.List;

/**
 * 決済サービスの窓口。注文の処理はこのインターフェースだけを通して支払いを行うので、
 * 疑似実装（FakePaymentGateway）を本物の決済サービスの実装に差し替えられる。
 *
 * <p>本物の決済サービスでは、カード番号は利用者のブラウザから決済サービスへ直接送られ、
 * このアプリは決済サービスが発行する「支払いトークン」だけを受け取る。カード番号は扱わない。
 */
public interface PaymentGateway {

    /** 画面に出す支払い方法の選択肢。 */
    List<PaymentMethod> availableMethods();

    /** この支払いトークンを受け付けるか。選択肢にない値（書き換えられたリクエスト）を拒否するために使う。 */
    default boolean supports(String paymentToken) {
        return availableMethods().stream().anyMatch(method -> method.token().equals(paymentToken));
    }

    /** 支払いを行う。カードが使えないなどの「失敗」は例外ではなく、結果（succeeded = false）で返す。 */
    ChargeResult charge(ChargeRequest request);

    /** 支払い済みの取引を全額返金する。失敗は例外ではなく、結果（succeeded = false）で返す。 */
    RefundResult refund(RefundRequest request);

    /** 支払い方法の選択肢。token を送ってもらい、label を画面に出す。 */
    record PaymentMethod(String token, String label) {
    }

    /**
     * 支払いの依頼。
     *
     * @param idempotencyKey 同じ依頼をくり返し送っても 1 回しか請求しないための値（本物の決済サービスに渡す）
     */
    record ChargeRequest(long orderId, String idempotencyKey, long amount, String paymentToken) {
        @Override
        public String toString() {
            // 支払いトークンはログに出さない
            return "ChargeRequest[orderId=" + orderId + ", amount=" + amount + "]";
        }
    }

    /** 支払いの結果。transactionId は決済サービスが発行する取引 ID（失敗したときも発行される）。 */
    record ChargeResult(boolean succeeded, String transactionId) {
    }

    /**
     * 返金の依頼。
     *
     * @param transactionId  返金する支払いの取引 ID（charge の結果で受け取ったもの）
     * @param idempotencyKey 同じ返金をくり返し送っても 1 回しか返金しないための値
     */
    record RefundRequest(long orderId, String transactionId, String idempotencyKey, long amount) {
    }

    /** 返金の結果。transactionId は返金の取引 ID（失敗したときは null）。 */
    record RefundResult(boolean succeeded, String transactionId) {
    }
}
