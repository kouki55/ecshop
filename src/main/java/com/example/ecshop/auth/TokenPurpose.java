package com.example.ecshop.auth;

/** トークンの用途。DB の ck_user_tokens_purpose と同じ値。 */
public enum TokenPurpose {
    EMAIL_VERIFICATION,
    PASSWORD_RESET
}
