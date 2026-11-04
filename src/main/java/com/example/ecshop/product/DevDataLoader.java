package com.example.ecshop.product;

import com.example.ecshop.pricing.TaxRate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 開発用プロファイル（dev）でだけ、練習用のカテゴリと商品を入れる。
 * Flyway のマイグレーションに入れると本番やテストの DB にも入ってしまうので、別の仕組みにしている。
 * カテゴリが 1 件でもあれば何もしない（何度起動しても二重に入らない）。
 *
 * <p>起動方法：{@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev}
 */
@Component
@Profile("dev")
public class DevDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataLoader.class);

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final Clock clock;

    public DevDataLoader(CategoryRepository categoryRepository, ProductRepository productRepository, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) {
            return;
        }
        Instant now = clock.instant();
        Category oil = categoryRepository.save(Category.create("エンジンオイル", 1, now));
        Category tire = categoryRepository.save(Category.create("タイヤ", 2, now));
        Category wiper = categoryRepository.save(Category.create("ワイパー", 3, now));
        Category battery = categoryRepository.save(Category.create("バッテリー", 4, now));
        Category care = categoryRepository.save(Category.create("カー用品", 5, now));

        List<Product> products = new ArrayList<>();
        // 引数：カテゴリ, 名前, 説明, 税抜価格, 税率, 在庫, 公開するか
        products.add(product(oil, "エンジンオイル 0W-20 4L", "ハイブリッド車・エコカー向けの低粘度オイルです。\n全合成油。", 3980, 50, true, now));
        products.add(product(oil, "エンジンオイル 5W-30 4L", "幅広い車種に使える定番の粘度です。", 2980, 120, true, now));
        products.add(product(oil, "エンジンオイル 10W-40 4L", "走行距離の多い車におすすめです。", 2480, 30, true, now));
        products.add(product(oil, "ディーゼル用エンジンオイル 5W-30 6L", "クリーンディーゼル車対応です。", 5980, 3, true, now));
        products.add(product(oil, "オイルフィルター（汎用）", "オイル交換 2 回に 1 回の交換が目安です。", 999, 80, true, now));
        products.add(product(tire, "夏タイヤ 195/65R15 1本", "低燃費タイヤ。ウェット性能 a ランク。", 9800, 40, true, now));
        products.add(product(tire, "夏タイヤ 215/45R17 1本", "スポーツ走行向けのタイヤです。", 15800, 12, true, now));
        products.add(product(tire, "スタッドレスタイヤ 195/65R15 1本", "凍結路面でよく止まる冬用タイヤです。", 13800, 0, true, now));
        products.add(product(tire, "スタッドレスタイヤ 205/55R16 1本", "雪道・凍結路面に対応します。", 16800, 4, true, now));
        products.add(product(tire, "タイヤ空気圧ゲージ", "デジタル表示の空気圧計です。", 1480, 60, true, now));
        products.add(product(wiper, "ワイパーブレード 600mm", "運転席側の定番サイズです。", 1280, 100, true, now));
        products.add(product(wiper, "ワイパーブレード 450mm", "助手席側の定番サイズです。", 1080, 100, true, now));
        products.add(product(wiper, "撥水ワイパー替えゴム 600mm", "交換するだけで撥水コートの効果が続きます。", 880, 70, true, now));
        products.add(product(wiper, "リアワイパー 300mm", "リアガラス用のワイパーです。", 780, 2, true, now));
        products.add(product(wiper, "雪用ワイパー 550mm", "凍結しにくい冬用ワイパーです。", 2480, 25, true, now));
        products.add(product(battery, "カーバッテリー 40B19L", "軽自動車・コンパクトカー向けです。", 6980, 20, true, now));
        products.add(product(battery, "カーバッテリー 60B24L", "普通車向けの定番サイズです。", 9980, 15, true, now));
        products.add(product(battery, "アイドリングストップ車用バッテリー M-42", "アイドリングストップ車専用です。", 12800, 8, true, now));
        products.add(product(battery, "ブースターケーブル 3m", "バッテリーが上がったときの応急用です。", 1980, 35, true, now));
        products.add(product(battery, "旧型バッテリー 34B19L（販売終了）", "販売を終了した商品です（非公開の確認用）。", 4980, 5, false, now));
        products.add(product(care, "洗車用シャンプー 1L", "すべての塗装色に使えます。", 980, 90, true, now));
        products.add(product(care, "ガラスコーティング剤", "塗って拭くだけで艶が出ます。", 3480, 45, true, now));
        products.add(product(care, "マイクロファイバータオル 5枚組", "傷がつきにくい洗車用タオルです。", 1280, 150, true, now));
        products.add(product(care, "車内用消臭剤", "エアコンの吹き出し口に取り付けます。", 680, 200, true, now));
        products.add(product(care, "ドライブレコーダー 前後2カメラ", "前後を同時に録画します。", 19800, 10, true, now));
        // 軽減税率（8%）の表示を確認するための商品（飲食料品）
        products.add(Product.create(care, "ドライブ用ミネラルウォーター 500ml×24本", "飲料水です（軽減税率 8% の対象）。",
                1980, TaxRate.REDUCED, 30, true, now));
        productRepository.saveAll(products);

        log.info("練習用データを投入しました（カテゴリ 5 件、商品 {} 件）", products.size());
    }

    private static Product product(Category category, String name, String description, long price,
                                   int stock, boolean published, Instant now) {
        return Product.create(category, name, description, price, TaxRate.STANDARD, stock, published, now);
    }
}
