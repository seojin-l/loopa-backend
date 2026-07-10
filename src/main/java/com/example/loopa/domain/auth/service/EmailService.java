package com.example.loopa.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVerificationCodeCreated(VerificationCodeCreatedEvent event) {
        sendVerificationCode(event.email(), event.code());
    }

    public void sendVerificationCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[Loopa] 이메일 인증번호 안내");
        message.setText("""
                안녕하세요, Loopa입니다.

                이메일 인증번호는 아래와 같습니다.

                인증번호: %s

                인증번호는 10분 동안 유효합니다.
                """.formatted(code));

        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.error("이메일 발송 실패: email={}", email, e);
        }
    }
}