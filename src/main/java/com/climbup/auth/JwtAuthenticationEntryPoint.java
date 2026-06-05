package com.climbup.auth;

import com.climbup.common.ErrorCode;
import com.climbup.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;


import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    //인증 안 된 요청이 보호된 자원에 도달하려할 때 Spring이 해당 메서드를 호출
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException autException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value()); // 401로 지정
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8"); // 한글 메시지 깨짐 방지

        ErrorResponse body = ErrorResponse.of(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.");
        objectMapper.writeValue(response.getWriter(), body); // 에러 envelope를 JSON으로 직접 씀
    }
}
