package com.aitaskmanager.repository.dto.login;

import lombok.Data;

/**
 * リフレッシュトークンリクエストDTO
 */
@Data
public class RefreshRequest {
    /** リフレッシュトークン */
    private String refreshToken;
}