package com.example.loopa.domain.response.entity;

import com.example.loopa.domain.survey.entity.Survey;
import com.example.loopa.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "survey_responses",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_response_member", columnNames = {"survey_id", "respondent_id"}),
                @UniqueConstraint(name = "uk_response_guest", columnNames = {"survey_id", "guest_key"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "respondent_id")
    private User respondent;

    @Column(name = "guest_key", length = 64)
    private String guestKey;

    @Column(name = "earned_token", nullable = false)
    private Integer earnedToken;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Answer> answers = new ArrayList<>();

    // 회원 응답
    public static SurveyResponse ofMember(Survey survey, User respondent) {
        SurveyResponse r = new SurveyResponse();
        r.survey = survey;
        r.respondent = respondent;
        r.guestKey = null;
        r.earnedToken = 0;
        r.submittedAt = LocalDateTime.now();
        return r;
    }

    // 게스트 응답
    public static SurveyResponse ofGuest(Survey survey, String guestKey) {
        SurveyResponse r = new SurveyResponse();
        r.survey = survey;
        r.respondent = null;
        r.guestKey = guestKey;
        r.earnedToken = 0;
        r.submittedAt = LocalDateTime.now();
        return r;
    }

    public void updateEarnedToken(int earned) {
        this.earnedToken = earned;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
