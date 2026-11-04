package com.example.ecshop.product;

/**
 * 画面に出す在庫の状態。正確な在庫数は出さない（売れ行きなどの社内情報を見せないため）。
 */
public enum StockStatus {
    IN_STOCK("在庫あり"),
    LOW("残りわずか"),
    OUT_OF_STOCK("在庫切れ");

    /** この個数以下なら「残りわずか」。 */
    public static final int LOW_STOCK_THRESHOLD = 5;

    private final String label;

    StockStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static StockStatus of(int stock) {
        if (stock <= 0) {
            return OUT_OF_STOCK;
        }
        return stock <= LOW_STOCK_THRESHOLD ? LOW : IN_STOCK;
    }
}
