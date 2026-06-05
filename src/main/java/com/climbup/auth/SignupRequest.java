package com.climbup.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest( // 회원가입 로직으로 record를 통해서 getter 내용을 내장하게 만든다. (setter는 포함되지 않는다. 따라서, record 파일에서는 불변객체만을 다룬다.)
        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8)
        String password,

        @NotBlank @Size(min = 2, max = 20)
        String nickname
) {
}
