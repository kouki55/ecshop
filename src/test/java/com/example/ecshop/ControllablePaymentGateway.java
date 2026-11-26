package com.example.ecshop;

import com.example.ecshop.payment.FakePaymentGateway;
import com.example.ecshop.payment.PaymentGateway;
import java.util.List;

/**
 * テスト用の決済。普段は疑似決済（FakePaymentGateway）と同じ動きをし、
 * {@link #failRefunds()} を呼ぶと返金を失敗させられる（疑似決済の返金はいつも成功するため）。
 */
public class ControllablePaymentGateway implements PaymentGateway {

    private final FakePaymentGateway delegate = new FakePaymentGateway();
    private volatile boolean refundFails;
    private volatile int refundCount;

    @Override
    public List<PaymentMethod> availableMethods() {
        return delegate.availableMethods();
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        return delegate.charge(request);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        refundCount++;
        return refundFails ? new RefundResult(false, null) : delegate.refund(request);
    }

    public void failRefunds() {
        refundFails = true;
    }

    /** 返金を依頼された回数。 */
    public int refundCount() {
        return refundCount;
    }

    /** 各テストの最初に呼んで、前のテストの設定を消す。 */
    public void reset() {
        refundFails = false;
        refundCount = 0;
    }
}
