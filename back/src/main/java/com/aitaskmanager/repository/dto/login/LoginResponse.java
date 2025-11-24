package com.aitaskmanager.repository.dto.login;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
/**
 * ログインレスポンスDTO
 */
public class LoginResponse {
    /** アクセストークン */
    private String accessToken;
    /** リフレッシュトークン */
    private String refreshToken;
}