package com.example.ecshop.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 住所の入力フォーム。持ち主（userId）や ID は持たせない。
 * フォームに userId を紛れ込ませて送られても、受け取る項目がないので無視される。
 */
public class AddressForm {

    @NotBlank(message = "宛名を入力してください")
    @Size(max = 100, message = "宛名は100文字以内で入力してください")
    private String recipientName;

    @NotBlank(message = "郵便番号を入力してください")
    @Pattern(regexp = "[0-9]{7}", message = "郵便番号は7桁の数字で入力してください（ハイフンは省略可）")
    private String postalCode;

    @NotBlank(message = "都道府県を選択してください")
    @ValidPrefecture
    private String prefecture;

    @NotBlank(message = "市区町村を入力してください")
    @Size(max = 100, message = "市区町村は100文字以内で入力してください")
    private String city;

    @NotBlank(message = "番地を入力してください")
    @Size(max = 200, message = "番地は200文字以内で入力してください")
    private String addressLine1;

    @Size(max = 200, message = "建物名・部屋番号は200文字以内で入力してください")
    private String addressLine2;

    @NotBlank(message = "電話番号を入力してください")
    @Pattern(regexp = "[0-9]{10,11}", message = "電話番号は10桁または11桁の数字で入力してください（ハイフンは省略可）")
    private String phone;

    /** 保存済みの住所から、変更画面の初期値を作る。 */
    public static AddressForm from(Address address) {
        AddressForm form = new AddressForm();
        form.recipientName = address.getRecipientName();
        form.postalCode = address.getPostalCode();
        form.prefecture = address.getPrefecture();
        form.city = address.getCity();
        form.addressLine1 = address.getAddressLine1();
        form.addressLine2 = address.getAddressLine2();
        form.phone = address.getPhone();
        return form;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = strip(recipientName);
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = digitsOnly(postalCode);
    }

    public String getPrefecture() {
        return prefecture;
    }

    public void setPrefecture(String prefecture) {
        this.prefecture = strip(prefecture);
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = strip(city);
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = strip(addressLine1);
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        // 任意項目。空欄は「なし」（null）として保存する
        String stripped = strip(addressLine2);
        this.addressLine2 = stripped == null || stripped.isEmpty() ? null : stripped;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = digitsOnly(phone);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    /** 「123-4567」「03-1234-5678」のようなハイフン付きの入力も受け付け、数字だけにそろえる。 */
    private static String digitsOnly(String value) {
        return value == null ? null : value.strip().replace("-", "").replace("－", "");
    }

    @Override
    public String toString() {
        // 住所・電話番号は個人情報なのでログに出さない
        return "AddressForm[***]";
    }
}
