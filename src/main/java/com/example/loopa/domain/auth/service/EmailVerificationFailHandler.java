package com.example.loopa.domain.auth.service;

import com.example.loopa.domain.auth.entity.EmailVerification;
import com.example.loopa.domain.auth.repository.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationFailHandler {

    private final EmailVerificationRepository emailVerificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailedAttempt(Long emailVerificationId) {
        EmailVerification emailVerification = emailVerificationRepository
                .findById(emailVerificationId).orElse(null);
        if (emailVerification == null) {
            return false;
        }

        emailVerification.incrementAttempt();
        if (emailVerification.isMaxAttemptExceeded()) {
            emailVerification.invalidate();
        }

        return emailVerification.isMaxAttemptExceeded();
    }
}
