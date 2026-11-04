package com.example.ecshop.product;

/** 画面に渡すカテゴリの情報。 */
public record CategoryView(Long id, String name) {

    static CategoryView from(Category category) {
        return new CategoryView(category.getId(), category.getName());
    }
}
