package com.example.ecshop.auth;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 47 都道府県。住所フォームの選択肢と、入力値の検証（選択肢以外を受け付けない）に使う。
 * DB の addresses.prefecture には label（例：東京都）を保存する。
 */
public enum Prefecture {
    HOKKAIDO("北海道"), AOMORI("青森県"), IWATE("岩手県"), MIYAGI("宮城県"), AKITA("秋田県"),
    YAMAGATA("山形県"), FUKUSHIMA("福島県"), IBARAKI("茨城県"), TOCHIGI("栃木県"), GUNMA("群馬県"),
    SAITAMA("埼玉県"), CHIBA("千葉県"), TOKYO("東京都"), KANAGAWA("神奈川県"), NIIGATA("新潟県"),
    TOYAMA("富山県"), ISHIKAWA("石川県"), FUKUI("福井県"), YAMANASHI("山梨県"), NAGANO("長野県"),
    GIFU("岐阜県"), SHIZUOKA("静岡県"), AICHI("愛知県"), MIE("三重県"), SHIGA("滋賀県"),
    KYOTO("京都府"), OSAKA("大阪府"), HYOGO("兵庫県"), NARA("奈良県"), WAKAYAMA("和歌山県"),
    TOTTORI("鳥取県"), SHIMANE("島根県"), OKAYAMA("岡山県"), HIROSHIMA("広島県"), YAMAGUCHI("山口県"),
    TOKUSHIMA("徳島県"), KAGAWA("香川県"), EHIME("愛媛県"), KOCHI("高知県"), FUKUOKA("福岡県"),
    SAGA("佐賀県"), NAGASAKI("長崎県"), KUMAMOTO("熊本県"), OITA("大分県"), MIYAZAKI("宮崎県"),
    KAGOSHIMA("鹿児島県"), OKINAWA("沖縄県");

    // 入力値の検証用。外には返さない（返すと呼び出し側から中身を変えられるおそれがあるため）
    private static final Set<String> LABEL_SET = Set.copyOf(labels());

    private final String label;

    Prefecture(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 画面の選択肢（北から順）。呼ばれるたびに新しいリストを作って返す。 */
    public static List<String> labels() {
        return Arrays.stream(values()).map(Prefecture::label).toList();
    }

    public static boolean isValidLabel(String label) {
        return label != null && LABEL_SET.contains(label);
    }
}
