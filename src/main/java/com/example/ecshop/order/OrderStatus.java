package com.example.ecshop.order;

import java.util.Set;

/**
 * 注文の状態（orders.status）。受付 → 支払済 → 発送済 → 完了 の順に進み、
 * 発送前（受付・支払済）ならキャンセルに分岐できる。それ以外の変化は受け付けない。
 */
public enum OrderStatus {
    RECEIVED("受付"),
    PAID("支払済"),
    SHIPPED("発送済"),
    COMPLETED("完了"),
    CANCELLED("キャンセル");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** キャンセルできるか（発送前の受付・支払済だけ）。画面でボタンを出すかどうかにも使う。 */
    public boolean isCancellable() {
        return canChangeTo(CANCELLED);
    }

    /** この状態から next に変えてよいか。 */
    public boolean canChangeTo(OrderStatus next) {
        return nextStatuses().contains(next);
    }

    private Set<OrderStatus> nextStatuses() {
        return switch (this) {
            case RECEIVED -> Set.of(PAID, CANCELLED);
            case PAID -> Set.of(SHIPPED, CANCELLED);
            case SHIPPED -> Set.of(COMPLETED);
            // 完了とキャンセルは最後の状態で、ここから変えられない
            case COMPLETED, CANCELLED -> Set.of();
        };
    }
}
