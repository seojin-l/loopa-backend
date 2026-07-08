package com.example.loopa.domain.archive.entity;

import com.example.loopa.domain.survey.entity.Survey;
import com.example.loopa.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "archive_views",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_archive_view", columnNames = {"viewer_id", "survey_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchiveView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viewer_id", nullable = false)
    private User viewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @Column(name = "token_spent", nullable = false)
    private Integer tokenSpent;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    public ArchiveView(User viewer, Survey survey, int tokenSpent) {
        this.viewer = viewer;
        this.survey = survey;
        this.tokenSpent = tokenSpent;
        this.viewedAt = LocalDateTime.now();
    }
}
