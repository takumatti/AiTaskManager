package com.aitaskmanager.repository.dto.login;

import lombok.Data;

/**
 * ログインリクエストDTO
 */
@Data
public class LoginRequest {
    /** ログインID (ユーザーID または メールアドレス) */
    private String userId;
    /** パスワード */
    private String password;
}
