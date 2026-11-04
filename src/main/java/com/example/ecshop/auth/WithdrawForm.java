package com.example.ecshop.auth;

import jakarta.validation.constraints.NotEmpty;

/** 退会の確認フォーム。本人確認のため、現在のパスワードを入力してもらう。 */
public class WithdrawForm {

    @NotEmpty(message = "パスワードを入力してください")
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "WithdrawForm[password=***]";
    }
}
