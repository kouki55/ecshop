-- =========================================================
-- 支払いの返金（会員による注文キャンセル用）
-- 支払いに成功した行（SUCCEEDED）を返金すると REFUNDED になり、返金の取引 ID と日時を記録する
-- =========================================================
ALTER TABLE payments DROP CONSTRAINT ck_payments_status;
ALTER TABLE payments
    ADD CONSTRAINT ck_payments_status CHECK (status IN ('SUCCEEDED', 'FAILED', 'REFUNDED'));

ALTER TABLE payments ADD COLUMN refund_transaction_id VARCHAR(64);
ALTER TABLE payments ADD COLUMN refunded_at TIMESTAMPTZ;

ALTER TABLE payments
    ADD CONSTRAINT uq_payments_refund_transaction_id UNIQUE (refund_transaction_id);

-- REFUNDED のときだけ、返金の取引 ID と日時の両方が入る（片方だけ・ほかの状態で入る、は許さない）
ALTER TABLE payments
    ADD CONSTRAINT ck_payments_refund CHECK (
        (status = 'REFUNDED') = (refund_transaction_id IS NOT NULL)
        AND (status = 'REFUNDED') = (refunded_at IS NOT NULL)
    );

-- 1 つの注文で「成功した支払い」は 1 件だけ。返金済みの行も、もとは成功した支払いなので数に含める
DROP INDEX ux_payments_order_succeeded;
CREATE UNIQUE INDEX ux_payments_order_succeeded ON payments (order_id) WHERE status IN ('SUCCEEDED', 'REFUNDED');
