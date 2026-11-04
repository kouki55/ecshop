package com.example.ecshop.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecshop.TestBeansConfiguration;
import com.example.ecshop.TestcontainersConfiguration;
import com.example.ecshop.pricing.TaxRate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * dev プロファイルで起動したときの練習用データ。
 * ほかのテストとは別の Spring コンテキストになり、PostgreSQL のコンテナも別に起動するので、
 * 空の DB に入れたときの件数をそのまま確かめられる。
 */
@SpringBootTest
@ActiveProfiles("dev")
@Import({TestcontainersConfiguration.class, TestBeansConfiguration.class})
class DevDataLoaderTest {

    @Autowired
    private DevDataLoader devDataLoader;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;

    @Test
    void 起動時に練習用データが入り2回目は何もしない() {
        // 起動時に 1 回目が動いている
        assertThat(categoryRepository.count()).isEqualTo(5);
        long products = productRepository.count();
        assertThat(products).isGreaterThan(ProductService.PAGE_SIZE);

        devDataLoader.run(new DefaultApplicationArguments());

        assertThat(categoryRepository.count()).isEqualTo(5);
        assertThat(productRepository.count()).isEqualTo(products);
    }

    @Test
    void 動作確認用に非公開と在庫切れと軽減税率の商品が含まれる() {
        assertThat(productRepository.findAll())
                .anyMatch(p -> !p.isPublished())
                .anyMatch(p -> p.getStock() == 0)
                .anyMatch(p -> p.getStock() > 0 && p.getStock() <= StockStatus.LOW_STOCK_THRESHOLD)
                .anyMatch(p -> p.getTaxRate() == TaxRate.REDUCED);
    }
}
