package com.example.ecshop;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

/**
 * テスト用のメール送信。実際には送らず、送ろうとしたメールを記録する。
 */
public class CapturingMailSender implements MailSender {

    private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(SimpleMailMessage... messages) {
        sent.addAll(List.of(messages));
    }

    /** 指定した宛先に送られたメール（古い順）。 */
    public List<SimpleMailMessage> sentTo(String email) {
        return sent.stream()
                .filter(m -> m.getTo() != null && List.of(m.getTo()).contains(email))
                .toList();
    }

    public void clear() {
        sent.clear();
    }
}
