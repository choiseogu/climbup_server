package com.climbup.auth;

import com.climbup.user.UserDto;

public record AuthResponse( // 회원가입/로그인 성공 시 돌려줄 최종 답
        String accessToken, // 발급한 JWT 문자열
        UserDto user // DTO 그릇으로 안전한 필드만 담아서 정보 반환
) {
}
