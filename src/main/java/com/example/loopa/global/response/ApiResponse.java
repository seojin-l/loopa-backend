package com.example.loopa.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.loopa.global.error.code.BaseErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T> (
        boolean isSuccess,
        String code,
        String message,
        T result
){
    public static <T> ApiResponse<T> success(T result) {
        return new ApiResponse<>(true,"COMMON_200","요청에 성공했습니다.",result);
    }

    public static <T> ApiResponse<T> success(String message, T result) {
        return new ApiResponse<>(true, "COMMON_200", message, result);
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(
                true,
                "COMMON_200",
                message,
                null
        );
    }

    public static ApiResponse<Void> success(){
        return success(null);
    }

    public static <T> ApiResponse<T> fail(BaseErrorCode errorCode, T result) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), result);
    }

    public static ApiResponse<Void> fail(BaseErrorCode errorCode) {
        return fail(errorCode,null);
    }
}