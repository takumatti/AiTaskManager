package com.aitaskmanager.security;

import java.util.Map;

import org.springframework.security.core.Authentication;

/**
 * SecurityContextからJWTクレームを安全に取り出すユーティリティ。
 */
public final class AuthUtils {

    /** プライベートコンストラクタ */
    private AuthUtils() {}

    /**
     * Authentication から JWT クレーム群（claims）を安全に取り出す
     *
     * @param auth 認証情報。null の場合は null を返します。
     * @return 抽出した claims の Map。取得できない場合は null。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractClaims(Authentication auth) {
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        // Spring Security OAuth2のJwtの場合、principalにclaimsを持つことがある
        // 直接型に依存せず、一般的な Map にフォールバック
        // Jwt型に依存せず、一般的なgetterにフォールバック
        try {
            var m = principal.getClass().getMethod("getClaims");
            Object claimsObj = m.invoke(principal);
            if (claimsObj instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (Exception ignored) {}
        if (principal instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        // details に入っている場合も考慮
        Object details = auth.getDetails();
        if (details instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    /**
     * 認証情報からユーザーSIDを取得する
     * 
     * @param auth 認証情報
     * @return ユーザーSID、存在しない場合はnull
     */
    public static Integer getUserSid(Authentication auth) {
        Map<String, Object> claims = extractClaims(auth);
        if (claims == null) return null;
        Object v = claims.get("uid");
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.valueOf(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /**
     * 認証情報からプランIDを取得する
     * 
     * @param auth 認証情報
     * @return プランSID、存在しない場合はnull
     */
    public static Integer getPlanId(Authentication auth) {
        Map<String, Object> claims = extractClaims(auth);
        if (claims == null) return null;
        Object v = claims.get("plan_id");
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.valueOf(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
