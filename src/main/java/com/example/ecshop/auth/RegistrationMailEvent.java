package com.example.ecshop.auth;

/**
 * 会員登録で送るメールの依頼。RegistrationService が発行し、
 * トランザクションのコミット後に RegistrationMailSender が受け取って送信する。
 */
public sealed interface RegistrationMailEvent {

    String email();

    /** 確認リンクのメール。token はメールにだけ載せる生の値。 */
    record VerificationRequested(String email, String name, String token) implements RegistrationMailEvent {
        @Override
        public String toString() {
            // record の自動 toString はトークンやメールアドレスまで出してしまうので伏せる
            return "VerificationRequested[***]";
        }
    }

    /** 登録済みのアドレスで再登録されたことを知らせるメール。 */
    record AlreadyRegistered(String email, String name) implements RegistrationMailEvent {
        @Override
        public String toString() {
            return "AlreadyRegistered[***]";
        }
    }
}
