package com.example.loopa.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

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

        mailSender.send(message);
    }
}