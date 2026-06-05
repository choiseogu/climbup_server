package com.climbup.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController // 반환값을 JSON 응답 본문으로 만들어줌.
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 회원가입 -> 성공 시 201
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) { // 요청 JSON을 SignupRequest 객체로 반환 (JSON -> 자바 객체 역직렬화)
        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 로그인 -> 성공 시 200
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) { //@Valid 내용이 있어 앞서 DTO에 붙여놓은 검증 로직이 돌아감.
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
