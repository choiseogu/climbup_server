package com.climbup.user;

import com.climbup.common.ApiException;
import com.climbup.common.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // 내 정보 조회 (JWT 인증 필수)
    @GetMapping("/me")
    public UserDto getMe(@AuthenticationPrincipal Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        return UserDto.from(user);
    }
}
