package com.demo.aidocumentassistant.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AiCallException.class)
    ProblemDetail aiFailed(AiCallException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "The AI model is unavailable. Try again.");
    }
}
