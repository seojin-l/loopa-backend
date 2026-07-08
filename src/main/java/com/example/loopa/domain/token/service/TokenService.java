package com.example.loopa.domain.token.service;

import com.example.loopa.domain.token.entity.TokenTransaction;
import com.example.loopa.domain.token.entity.TokenTxType;
import com.example.loopa.domain.token.repository.TokenTransactionRepository;
import com.example.loopa.domain.user.entity.User;
import com.example.loopa.domain.user.repository.UserRepository;
import com.example.loopa.global.error.code.GlobalErrorCode;
import com.example.loopa.global.error.code.TokenErrorCode;
import com.example.loopa.global.error.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final UserRepository userRepository;
    private final TokenTransactionRepository tokenTransactionRepository;

    @Transactional
    public int record(Long userId, TokenTxType type, int amount,
                      Long relatedSurveyId, Long relatedResponseId) {

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));

        int after = user.getTokenBalance() + amount;
        if (after < 0) {
            throw new GeneralException(TokenErrorCode.INSUFFICIENT_BALANCE);
        }

        user.updateTokenBalance(after);

        TokenTransaction tx = TokenTransaction.of(user, type, amount, after,
                relatedSurveyId, relatedResponseId);
        tokenTransactionRepository.save(tx);

        return after;
    }
}
