package com.climbup.common;

public record ErrorResponse(ErrorDetail error) { //에러를 담는 도구 / record : 데이터를 담기만 하는 클래스 -> 생성자, getter 등 자동 생성

    public record ErrorDetail(String code, String message) {
    }

    public static ErrorResponse of(ErrorCode code, String message) { // 편하게 만드는 지름길 메서드로, code.name()은 enum을 문자열로 바꿔준다.
        return new ErrorResponse(new ErrorDetail(code.name(), message));
    }
}
