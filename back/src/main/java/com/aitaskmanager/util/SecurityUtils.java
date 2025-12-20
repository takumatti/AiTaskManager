package com.aitaskmanager.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

/**
 * セキュリティ関連のユーティリティクラス
 */
public final class SecurityUtils {

    /**
     * フィルターで設定されたリクエスト属性から現在のユーザーIDを取得（存在しない場合はnull）
     */
    public static Integer getCurrentUserId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            Object v = req.getAttribute("X-User-Id");
            if (v instanceof Integer) return (Integer) v;
            if (v instanceof Number) return ((Number) v).intValue();
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}