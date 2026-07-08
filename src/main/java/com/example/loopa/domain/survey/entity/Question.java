package com.example.loopa.domain.survey.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;

    @Column(nullable = false, length = 200)
    private String content;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired;

    @Column(name = "allow_multiple", nullable = false)
    private Boolean allowMultiple;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuestionOption> options = new ArrayList<>();

    public Question(Survey survey, Integer questionOrder, QuestionType type,
                    String content, Boolean isRequired, Boolean allowMultiple) {
        this.survey = survey;
        this.questionOrder = questionOrder;
        this.type = type;
        this.content = content;
        this.isRequired = isRequired;
        this.allowMultiple = allowMultiple;
    }

    public void addOption(QuestionOption option) {
        this.options.add(option);
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
