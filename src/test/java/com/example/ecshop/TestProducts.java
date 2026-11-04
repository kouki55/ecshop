package com.example.ecshop;

import com.example.ecshop.product.Category;
import com.example.ecshop.product.CategoryRepository;
import com.example.ecshop.product.Product;
import com.example.ecshop.product.ProductRepository;
import com.example.ecshop.product.TaxRate;
import java.util.UUID;

/**
 * テスト用のカテゴリと商品を作る。テストは同じ DB を共有するので、件数を確かめるテストは
 * 毎回新しいカテゴリ（名前はランダム）を作り、その中だけで数える。
 */
public class TestProducts {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MutableClock clock;

    public TestProducts(CategoryRepository categoryRepository, ProductRepository productRepository,
                        MutableClock clock) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.clock = clock;
    }

    public Category category() {
        return categoryRepository.save(Category.create("テストカテゴリ-" + UUID.randomUUID(), 100, clock.instant()));
    }

    /** 公開中・在庫 10・税抜 1,000 円・税率 10% の商品。 */
    public Product published(Category category, String name) {
        return product(category, name, 1000, TaxRate.STANDARD, 10, true);
    }

    public Product unpublished(Category category, String name) {
        return product(category, name, 1000, TaxRate.STANDARD, 10, false);
    }

    public Product product(Category category, String name, long price, TaxRate taxRate, int stock,
                           boolean published) {
        return productRepository.save(Product.create(category, name, "テスト用の商品です。", price, taxRate, stock,
                published, clock.instant()));
    }

    public static String randomName() {
        return "テスト商品-" + UUID.randomUUID();
    }
}
