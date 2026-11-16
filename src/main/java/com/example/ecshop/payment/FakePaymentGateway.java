package com.example.ecshop.payment;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 疑似決済。実際にはどこにも接続せず、支払いトークンで成功・失敗を決める。
 * 本物の決済サービスのテスト用カード（「必ず成功する番号」「必ず失敗する番号」）と同じ考え方。
 */
@Component
public class FakePaymentGateway implements PaymentGateway {

    public static final String SUCCESS_TOKEN = "fake_success";
    public static final String DECLINE_TOKEN = "fake_decline";

    private static final List<PaymentMethod> METHODS = List.of(
            new PaymentMethod(SUCCESS_TOKEN, "テスト用カード（支払いが成功する）"),
            new PaymentMethod(DECLINE_TOKEN, "テスト用カード（支払いが失敗する）"));

    private final SecureRandom random = new SecureRandom();

    @Override
    public List<PaymentMethod> availableMethods() {
        return METHODS;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        if (!supports(request.paymentToken())) {
            throw new IllegalArgumentException("対応していない支払い方法です");
        }
        return new ChargeResult(SUCCESS_TOKEN.equals(request.paymentToken()), newTransactionId());
    }

    /** 取引 ID。推測されないよう SecureRandom で作る（payments.transaction_id は 64 文字まで）。 */
    private String newTransactionId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return "fake_" + HexFormat.of().formatHex(bytes);
    }
}
