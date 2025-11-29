package com.aitaskmanager.repository.dto.login;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ログインレスポンスDTO
 */
@Getter
@AllArgsConstructor
public class LoginResponse {
    /** アクセストークン */
    private String accessToken;
    /** リフレッシュトークン */
    private String refreshToken;
    /** ログインユーザーID */
    private Integer userId;
}