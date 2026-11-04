package com.example.ecshop.product;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
