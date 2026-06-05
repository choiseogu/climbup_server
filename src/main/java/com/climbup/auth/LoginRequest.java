package com.climbup.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest( // 로그인 요청 내용으로 record를 통해 getter를 내장하여 만든다.(setter는 포함되지 않는다. 따라서, record 파일에서는 불변객체만을 다룬다.) DB와의 대조로 로그인이 진행되니 검증 과정이 있을 필욘 없다.
        @NotBlank
        String email,

        @NotBlank
        String password
) {
}
