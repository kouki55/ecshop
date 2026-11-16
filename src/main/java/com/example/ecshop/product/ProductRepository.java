package com.example.ecshop.product;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 利用者向けの検索は、すべて「公開中（published = true）」を条件に含める。
 * 非公開の商品を ID だけで取得するメソッドは、利用者向けの画面からは使わない。
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    // @EntityGraph：商品と一緒にカテゴリも 1 回の SQL で読む（商品ごとにカテゴリを読む N+1 問題を防ぐ）
    @EntityGraph(attributePaths = "category")
    Page<Product> findByPublishedTrue(Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Page<Product> findByPublishedTrueAndCategoryId(Long categoryId, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndPublishedTrue(Long id);

    /**
     * 在庫を変える前に、商品の行を悲観ロック（SELECT ... FOR UPDATE）で取得する。
     * トランザクションが終わるまで、ほかの注文は同じ商品の行を読めずに待つ。
     * ID の小さい順にロックするのは、2 つの注文が同じ商品を違う順にロックして
     * お互いを待ち続ける（デッドロック）のを防ぐため。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
