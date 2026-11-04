package com.example.ecshop.cart;

/**
 * カートの明細の状態。カートに入れたあとで商品が非公開になったり在庫が減ったりすることがあるので、
 * 表示のたびに判定する。OK 以外の明細は合計に含めない（注文もできない）。
 */
public enum LineStatus {
    OK(""),
    DISCONTINUED("販売終了"),
    OUT_OF_STOCK("在庫切れ"),
    INSUFFICIENT_STOCK("在庫不足（数量を減らしてください）");

    private final String label;

    LineStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean purchasable() {
        return this == OK;
    }

    static LineStatus of(boolean published, int stock, int quantity) {
        if (!published) {
            return DISCONTINUED;
        }
        if (stock <= 0) {
            return OUT_OF_STOCK;
        }
        return quantity > stock ? INSUFFICIENT_STOCK : OK;
    }
}
