package com.example.loopa.domain.token.service;

import com.example.loopa.domain.token.repository.TokenTransactionRepository;
import com.example.loopa.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final UserRepository userRepository;
    private final TokenTransactionRepository tokenTransactionRepository;

    // record(userId, type, amount, relatedSurveyId, relatedResponseId)
    // → 비관적 락으로 User 조회 → 잔액 계산 → 부족 시 TOKEN_001 → balance 갱신 + tx 저장 → 결과 잔액 반환
}
