package com.aitaskmanager.repository.dto.login;

import lombok.Data;

/**
 * ログインリクエストDTO
 */
@Data
public class LoginRequest {
    /** ユーザー名 */
    private String username;
    /** パスワード */
    private String password;
}
