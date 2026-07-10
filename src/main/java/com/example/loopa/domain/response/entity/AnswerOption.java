package com.example.loopa.domain.response.entity;

import com.example.loopa.domain.survey.entity.QuestionOption;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "answer_options")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnswerOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id", nullable = false)
    private QuestionOption option;

    public AnswerOption(Answer answer, QuestionOption option) {
        this.answer = answer;
        this.option = option;
    }
}
