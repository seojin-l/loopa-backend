package com.example.loopa.domain.token.entity;

import com.example.loopa.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "token_transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TokenTxType type;

    @Column(nullable = false)
    private Integer amount;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(name = "related_survey_id")
    private Long relatedSurveyId;

    @Column(name = "related_response_id")
    private Long relatedResponseId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static TokenTransaction of(User user, TokenTxType type, int amount, int balanceAfter,
                                      Long relatedSurveyId, Long relatedResponseId) {
        TokenTransaction tx = new TokenTransaction();
        tx.user = user;
        tx.type = type;
        tx.amount = amount;
        tx.balanceAfter = balanceAfter;
        tx.relatedSurveyId = relatedSurveyId;
        tx.relatedResponseId = relatedResponseId;
        return tx;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
