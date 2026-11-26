package com.example.ecshop.order;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 会員から操作される注文の取得は、必ずログイン中の会員 ID（userId）を条件に含める。
 * JPQL では、エンティティ名 CustomerOrder で書く（Order は予約語のため）。
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    /** 会員の注文を 1 ページ分（並び順は Pageable で指定する）。 */
    Page<Order> findByUserId(Long userId, Pageable pageable);

    /**
     * 一覧に出す商品名を、複数の注文の分まとめて取得する。
     * 注文ごとに明細を読み込むと、1 ページ 20 件で 20 回 SQL が走る（N+1 問題）ため、1 回で取る。
     */
    @Query("""
            select new com.example.ecshop.order.OrderItemName(i.order.id, i.productName)
            from OrderItem i where i.order.id in :orderIds order by i.id
            """)
    List<OrderItemName> findItemNamesByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    /** 支払い・キャンセルの前に、注文の行をロックして取得する（連打で二重に処理しないため）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id and o.userId = :userId")
    Optional<Order> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    /** 自動キャンセル用（会員の操作ではないので userId の条件はない）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    Optional<Order> findByIdempotencyKey(UUID idempotencyKey);

    /** 指定日時より前に受け付けて、まだ支払われていない注文の ID（自動キャンセルの対象）。 */
    @Query("select o.id from CustomerOrder o where o.status = :status and o.orderedAt < :before order by o.id")
    List<Long> findIdsByStatusOrderedBefore(@Param("status") OrderStatus status, @Param("before") Instant before);
}
