package com.example.loopa.domain.user.service;

import com.example.loopa.domain.user.dto.response.UserMeResponse;
import com.example.loopa.domain.user.entity.User;
import com.example.loopa.domain.user.repository.UserRepository;
import com.example.loopa.global.error.code.GlobalErrorCode;
import com.example.loopa.global.error.exception.GeneralException;
import com.example.loopa.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;


    public UserMeResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE));

        return UserMeResponse.from(user);
    }
}
