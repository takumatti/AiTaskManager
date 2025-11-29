package com.aitaskmanager.repository.dto.login;

import lombok.Data;

/**
 * ログインリクエストDTO
 */
@Data
public class LoginRequest {
    /** ログインID (ユーザー名 または メールアドレス) */
    private String username;
    /** パスワード */
    private String password;
}
