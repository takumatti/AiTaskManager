package com.aitaskmanager.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ログ出力を統一するためのユーティリティ。
 */
public final class LogUtil {

    private LogUtil() {}

    /**
     * コントローラ用の統一ログ（入口ログなど）。
     * 例: [Controller] tasks.delete id=123 userId=1 username=alice invoked
     * @param clazz ログを出力するクラス
     * @param domainDotAction ドメインとアクションをドットでつなげた文字列（例: "tasks.delete"）
     * @param userId ユーザーID
     * @param username ユーザー名
     * @param suffix ログの末尾に付与する文字列（例: "invoked", "completed"）
     */
    public static void controller(Class<?> clazz, String domainDotAction, Integer userId, String username, String suffix) {
        Logger log = LoggerFactory.getLogger(clazz);
        log.info("[Controller] {} userId={} username={} {}", domainDotAction, userId, username, suffix);
    }

    /**
     * サービス用の統一ログ（入口/完了）。
     * 例: [Service] tasks.delete taskId=123 userId=1 started
     * @param clazz ログを出力するクラス
     * @param domainDotAction ドメインとアクションをドットでつなげた文字列（例: "tasks.delete"）
     * @param kvPairs キーと値のペア文字列（例: "taskId=123 userId=1"）
     * @param suffix ログの末尾に付与する文字列（例:"started", "completed"）
     */
    public static void service(Class<?> clazz, String domainDotAction, String kvPairs, String suffix) {
        Logger log = LoggerFactory.getLogger(clazz);
        log.info("[Service] {} {} {}", domainDotAction, kvPairs, suffix);
    }
}
