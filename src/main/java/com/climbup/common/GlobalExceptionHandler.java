package com.climbup.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice //해당 클래스는 모든 컨트롤러에서 던져지는 예외를 감시한다 의 어노테이션 (다양한 컨트롤러에서의 ApiException -> GlobalExceptionHandler -> @ExceptionHandler 메서드 실행 -> JSON 응답으로 변환
public class GlobalExceptionHandler {

    //직접 던진 ApiException 처리
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        ErrorResponse body = ErrorResponse.of(e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(body);
    }

    //검증 실패(@Valid) 처리 -> 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() +": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ErrorResponse body = ErrorResponse.of(ErrorCode.VALIDATION_ERROR, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    //예상 못 한 모든 예외 -> 500 (내부 정보 노출은 금지)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // TODO: 실제로는 여기서 로그를 남겨야 하므로 나중에 Logger 추가 예정
        ErrorResponse body = ErrorResponse.of(ErrorCode.NOT_FOUND, "서버 내부 오류가 발생했습니다.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
