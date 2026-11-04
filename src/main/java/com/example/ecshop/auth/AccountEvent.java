package com.example.ecshop.auth;

/**
 * パスワード再設定・退会で発行するイベント。トランザクションのコミット後に、
 * AccountMailSender（メール送信）と UserSessionService（セッションの失効）が受け取る。
 */
public sealed interface AccountEvent {

    /** 再設定リンクのメールを送る。token はメールにだけ載せる生の値。 */
    record PasswordResetRequested(String email, String name, String token) implements AccountEvent {
        @Override
        public String toString() {
            // record の自動 toString はトークンやメールアドレスまで出してしまうので伏せる
            return "PasswordResetRequested[***]";
        }
    }

    /** パスワードが変更された。通知メールを送り、その会員のセッションをすべて失効させる。 */
    record PasswordChanged(long userId, String email, String name) implements AccountEvent {
        @Override
        public String toString() {
            return "PasswordChanged[userId=" + userId + "]";
        }
    }

    /** 退会した。email と name は匿名化する前の値（完了メールの宛先）。 */
    record Withdrawn(long userId, String email, String name) implements AccountEvent {
        @Override
        public String toString() {
            return "Withdrawn[userId=" + userId + "]";
        }
    }
}
