package com.example.loopa.domain.auth.repository;

import com.example.loopa.domain.auth.entity.EmailVerification;
import com.example.loopa.domain.auth.entity.Purpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findTopByEmailAndPurposeOrderByCreatedAtDesc(
            String email,
            Purpose purpose
    );

    boolean existsByEmailAndPurposeAndVerifiedTrueAndExpiresAtAfter(
            String email,
            Purpose purpose,
            LocalDateTime now
    );

    void deleteAllByEmailAndPurpose(String email, Purpose purpose);
}
