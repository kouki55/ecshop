package com.example.ecshop.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 会員登録フォームの入力。エンティティ（User）を直接バインドせず、受け取りたい項目だけを持つ DTO。
 * パスワードの規則はクラスに付けた @AcceptablePassword で検証する。
 */
@AcceptablePassword
public class RegistrationForm {

    @NotBlank(message = "お名前を入力してください")
    @Size(max = 100, message = "お名前は100文字以内で入力してください")
    private String name;

    @NotBlank(message = "メールアドレスを入力してください")
    @Email(message = "メールアドレスの形式が正しくありません")
    @Size(max = 254, message = "メールアドレスは254文字以内で入力してください")
    private String email;

    // パスワードは前後の空白も含めてそのまま受け取る（strip しない）
    private String password;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        // コピー＆ペーストで入りがちな前後の空白だけを取り除く
        this.email = email == null ? null : email.strip();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        // ログなどにパスワードが出ないようにする
        return "RegistrationForm[name=***, email=***, password=***]";
    }
}
