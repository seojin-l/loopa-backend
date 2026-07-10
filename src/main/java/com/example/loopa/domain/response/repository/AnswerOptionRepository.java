package com.example.loopa.domain.response.repository;

import com.example.loopa.domain.response.entity.AnswerOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerOptionRepository extends JpaRepository<AnswerOption, Long> {
}
