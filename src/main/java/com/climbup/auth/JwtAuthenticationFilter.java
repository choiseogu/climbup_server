package com.climbup.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter { // 스프링이 제공하는 필터 베이스로 상속 받아 "요청 한 번당 딱 한 번만 실행"되게 보장해줌

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // 1. 헤더 없거나 Bearer로 시작 하지 않으면 -> 인증 시도 없이 통과
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. "Bearer " 뒤의 토큰만 잘라냄
        String token = authHeader.substring(7);

        // 3. 검증 후 진짜면 인증 도장 찍기
        if (jwtService.validateToken(token)) {
            Long userId = jwtService.extractUserId(token);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
            // 해당 내용을 통해서 인증 정보를 담는 Spring Securtiy의 표준 객체를 생성한다.
            // principal, credentials, authorities라는 매개변수를 가지고 이는 "1. 누구인지 2. 비밀번호(토큰으로 인증되었으니 null로 입력) 3. 권한 목록" 내용을 담는다.

            SecurityContextHolder.getContext().setAuthentication(authentication);
            // SecurityContextHolder를 통해서 현재 요청이 누구인지를 보관함에 저장. -> "해당 요청은 유저 5번의 인증 내용"임을 유지, 이는 나중 단계인 controller에서 사용되어 사용자 식별에 쓰임.
        }

        // 4. 다음 단계로 넘김
        filterChain.doFilter(request, response);
    }
}
