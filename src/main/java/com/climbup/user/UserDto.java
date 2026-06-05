package com.climbup.user;

import java.time.LocalDateTime;

public record UserDto(
        Long id,
        String email,
        String nickname,
        String provider,
        LocalDateTime createdAt
) {
    // User 엔티티 -> User Dto 변환 (안전한 필드만 골라서 담음, 비밀번호와 같은 내용은 담지 않는다.)
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProvider().name(),
                user.getCreatedAt()
        );
    }
}
