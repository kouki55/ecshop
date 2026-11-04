package com.example.ecshop.product;

/**
 * 画面に渡す商品の情報。エンティティを画面に直接渡さず、表示に必要な値だけをここに詰める
 * （在庫数そのものや公開フラグは含めない）。
 */
public record ProductView(
        Long id,
        String name,
        String description,
        Long categoryId,
        String categoryName,
        long price,
        long priceIncludingTax,
        int taxRatePercent,
        StockStatus stockStatus) {

    static ProductView from(Product product) {
        TaxRate taxRate = product.getTaxRate();
        return new ProductView(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getPrice(),
                taxRate.includeTax(product.getPrice()),
                taxRate.percent(),
                StockStatus.of(product.getStock()));
    }
}
