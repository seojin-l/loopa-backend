package com.example.loopa.domain.response.repository;

import com.example.loopa.domain.response.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
}
