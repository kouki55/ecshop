package com.example.ecshop.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByIdAsc(Long orderId);

    long countByOrderId(Long orderId);

    /** 注文の「成功した支払い」（一意インデックスがあるので 0 件か 1 件）。 */
    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
