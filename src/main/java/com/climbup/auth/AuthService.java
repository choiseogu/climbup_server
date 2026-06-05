package com.climbup.auth;

import com.climbup.common.ApiException;
import com.climbup.common.ErrorCode;
import com.climbup.user.User;
import com.climbup.user.UserDto;
import com.climbup.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // 필요한 부품 3개를 생성자로 주입 받음.
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    //회원가입
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        //1. 이메일 중복 검사 -> 중복 시 409
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_DUPLICATED, // 해당 내용에서 오류가 던져지면 spring에서 가로채 GlobalExceptionHandler로 보내줌.
                    "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT);
        }

        //2. 비밀번호 해싱 (평문 저장 절대 금지)
        String passwordHash = passwordEncoder.encode(request.password());

        //3. User 생성 후 저장
        User user = User.createLocal(request.email(), passwordHash, request.nickname());
        User saved = userRepository.save(user);

        //4. JWT 발급 + 응답 조립
        String token = jwtService.generateToken(saved.getId());
        return new AuthResponse(token, UserDto.from(saved));
    }

    //로그인
    @Transactional(readOnly = true) // 로그인은 내용을 읽기만 하므로 readonly = true 설정
    public AuthResponse login(LoginRequest request) {
        // 1. 이메일로 사용자 조회 -> 없으면 401
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS,
                        "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED));

        // 2. 입력 비번 vs 저장된 해시 비교 -> 틀리면 401
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS,
                    "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
        }

        // 3. JWT 발급 + 응답 조립
        String token = jwtService.generateToken(user.getId());
        return new AuthResponse(token, UserDto.from(user));
    }
}
