package com.example.ecshop.auth;

/** 会員の状態。DB の ck_users_status と同じ値。 */
public enum UserStatus {
    /** 仮登録（メールのリンクをまだ開いていない）。ログインできない。 */
    PENDING,
    /** 有効。ログインできる。 */
    ACTIVE,
    /** 退会済み。ログインできない。 */
    WITHDRAWN
}
