package com.example.ecshop.product;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 商品の一覧と詳細。公開中の商品だけを返す。
 * open-in-view を無効にしているので、カテゴリ名などはこのトランザクションの中で DTO に詰めてから返す。
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    /** 1 ページの件数。利用者には変えさせない（大きな値で大量に読ませないため）。 */
    public static final int PAGE_SIZE = 20;

    /**
     * 受け付けるページ番号の上限。DB に渡す読み飛ばし件数（page × 件数）が int に収まる範囲にし、
     * さらに現実的な件数に抑える。これより大きい番号は 400 にする。
     */
    public static final int MAX_PAGE = 10_000;

    // 並び順はサーバーで固定する（新しい順。同時刻なら ID の大きい順で、毎回同じ並びにする）
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryView> categories() {
        return categoryRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(CategoryView::from)
                .toList();
    }

    /**
     * 公開中の商品を新しい順に 1 ページ分返す。
     *
     * @param categoryId 絞り込むカテゴリ。null なら全カテゴリ
     * @param page       0 から始まるページ番号
     */
    public Page<ProductView> list(Long categoryId, int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, NEWEST_FIRST);
        if (categoryId == null) {
            return productRepository.findByPublishedTrue(pageable).map(ProductView::from);
        }
        // 存在しないカテゴリは「0 件」ではなく 404 にする（URL の誤りだと利用者にわかるように）
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return productRepository.findByPublishedTrueAndCategoryId(categoryId, pageable).map(ProductView::from);
    }

    /** 公開中の商品を返す。非公開の商品と存在しない商品は、区別できないよう同じ 404 にする。 */
    public ProductView get(Long id) {
        return productRepository.findByIdAndPublishedTrue(id)
                .map(ProductView::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
