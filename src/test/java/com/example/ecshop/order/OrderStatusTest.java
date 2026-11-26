package com.example.ecshop.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 注文の状態は「受付 → 支払済 → 発送済 → 完了」の順にだけ進み、キャンセルは発送前だけ。 */
class OrderStatusTest {

    @Test
    void 決められた順番にだけ進める() {
        assertNext(OrderStatus.RECEIVED, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED));
        assertNext(OrderStatus.PAID, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        assertNext(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.COMPLETED));
        assertNext(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        assertNext(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    @Test
    void キャンセルできるのは受付と支払済だけ() {
        assertThat(EnumSet.allOf(OrderStatus.class)).filteredOn(OrderStatus::isCancellable)
                .containsExactlyInAnyOrder(OrderStatus.RECEIVED, OrderStatus.PAID);
    }

    private static void assertNext(OrderStatus from, Set<OrderStatus> allowed) {
        for (OrderStatus to : OrderStatus.values()) {
            assertThat(from.canChangeTo(to)).as(from + " → " + to).isEqualTo(allowed.contains(to));
        }
    }
}
