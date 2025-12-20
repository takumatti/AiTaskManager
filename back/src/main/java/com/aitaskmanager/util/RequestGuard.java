package com.aitaskmanager.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * リクエスト共通ガード。現在のユーザーIDを取得し、存在しない場合は401を投げる。
 */
public final class RequestGuard {

    private static final Logger log = LoggerFactory.getLogger(RequestGuard.class);

    private RequestGuard() {}

    /**
     * 現在の認証コンテキストから userIdを取得。nullなら 401 を投げる。
     * @return userId
     */
    public static Integer requireUserId() {
        Integer userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            log.warn("[Guard] userId is null; unauthorized");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザーが存在しません");
        }
        return userId;
    }
}
