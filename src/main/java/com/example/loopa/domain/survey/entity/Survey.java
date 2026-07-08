package com.example.loopa.domain.survey.entity;

import com.example.loopa.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "surveys")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Survey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(length = 50)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "shared_to_archive", nullable = false)
    private Boolean sharedToArchive;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Question> questions = new ArrayList<>();

    public Survey(User creator, String title, String description, String target,
                  Category category, Integer estimatedMinutes,
                  LocalDate startDate, LocalDate endDate) {
        this.creator = creator;
        this.title = title;
        this.description = description;
        this.target = target;
        this.category = category;
        this.estimatedMinutes = estimatedMinutes;
        this.startDate = startDate;
        this.endDate = endDate;
        this.sharedToArchive = false;
        this.isDeleted = false;
    }

    public void addQuestion(Question question) {
        this.questions.add(question);
    }

    public void softDelete() {
        this.isDeleted = true;
    }

    public void shareToArchive() {
        this.sharedToArchive = true;
        this.archivedAt = LocalDateTime.now();
    }

    public boolean isClosed() {
        return LocalDate.now().isAfter(this.endDate);
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
