package com.aitaskmanager.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * リクエスト共通ガード。現在の内部数値ユーザーID（user_sid）を取得し、存在しない場合は401を投げる。
 */
public final class RequestGuard {

    private static final Logger log = LoggerFactory.getLogger(RequestGuard.class);

    private RequestGuard() {}

    /**
     * 現在の認証コンテキストから内部数値ID（user_sid）を取得。nullなら401を投げる。
     * 
     * @return userSid（Integer）
     */
    public static Integer requireUserSid() {
        Integer userSid = SecurityUtils.getCurrentUserId();
        if (userSid == null) {
            log.warn("[Guard] userSid is null; unauthorized");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザーが存在しません");
        }
        return userSid;
    }
}
