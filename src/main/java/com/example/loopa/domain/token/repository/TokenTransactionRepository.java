package com.example.loopa.domain.token.repository;

import com.example.loopa.domain.token.entity.TokenTransaction;
import com.example.loopa.domain.token.entity.TokenTxType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenTransactionRepository extends JpaRepository<TokenTransaction, Long> {

    boolean existsByRelatedSurveyIdAndType(Long relatedSurveyId, TokenTxType type);
}
