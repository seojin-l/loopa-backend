package com.example.loopa.domain.survey.repository;

import com.example.loopa.domain.survey.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {
}
