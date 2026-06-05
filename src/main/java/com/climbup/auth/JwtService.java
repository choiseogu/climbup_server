package com.climbup.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessExpMinutes;

    // 생성자: yml의 jwt.secret, jwt.access-exp-minutes 값을 주입 받는 생성자
    public JwtService (
            @Value("${jwt.secret}") String secret, // 실행 시 @Value 매개변수 내용은 yml 내용에서 가져와짐.
            @Value("${jwt.access-exp-minutes}") long accessExpMinutes
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)); // 가져온 jwt.secret을 기반으로 hs256 알고리즘을 가지고 서버에서 서명 값 생성
        this.accessExpMinutes = accessExpMinutes;
    }

    // 1. 토큰 만들기
    public String generateToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpMinutes * 60_000L);

        return Jwts.builder()
                .subject(String.valueOf(userId)) // sub = userId
                .issuedAt(now) // iat = 발급 시각
                .expiration(expiry) // exp = 만료 시각
                .signWith(signingKey) // 서명
                .compact(); // 최종 문자열로 압축 (서명 기반 토큰 완성)
    }

    // 2. 토크 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false; // 위조, 만료, 형식 오류에서 false 반환
        }
    }

    // 3. 토큰에서 userId 꺼내기
    public Long extractUserId(String token) {
        String subject = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return Long.parseLong(subject);
    }
}
