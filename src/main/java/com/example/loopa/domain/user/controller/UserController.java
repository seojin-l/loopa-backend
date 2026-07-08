package com.example.loopa.domain.user.controller;

import com.example.loopa.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // USER-01 내 정보 조회       GET /users/me
    // USER-02 등록한 설문 목록    GET /users/me/surveys
    // USER-03 열람한 설문 목록    GET /users/me/viewed-surveys
}
