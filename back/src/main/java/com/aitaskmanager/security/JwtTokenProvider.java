package com.aitaskmanager.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * JWTトークンの発行・検証を行うクラス
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token.expiration}")
    private long accessTokenExpiration; // ミリ秒指定（例: 15分）

    @Value("${jwt.refresh-token.expiration}")
    private long refreshTokenExpiration; // ミリ秒指定（例: 7日）

    /**
     * 署名用の SecretKey を返す
     * 
     * @return SecretKey
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * アクセストークンを生成（ユーザーIDをクレームに含める）
     * 
     * @param resolvedUserId 画面入力情報
     * @param userId ユーザーID
     * @param roles ユーザーロール一覧
     * @return 生成されたアクセストークン
     */
    public String generateAccessToken(String resolvedUserId, Integer userId, Integer planId, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        JwtBuilder builder = Jwts.builder()
                .setSubject(resolvedUserId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512);
        if (userId != null) builder.claim("uid", userId);
        if (planId != null) builder.claim("planId", planId);
        if (roles != null && !roles.isEmpty()) builder.claim("roles", roles);

        return builder.compact();
    }

    /**
     * リフレッシュトークンを生成（ユーザーIDをクレームに含める）
     * 
     * @param username ユーザー名
     * @param userId ユーザーID 
     * @param roles ユーザーロール一覧
     * @return 生成されたリフレッシュトークン
     */
    public String generateRefreshToken(String username, Integer userId, Integer planId, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

        JwtBuilder builder = Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512);
        if (userId != null) builder.claim("uid", userId);
        if (planId != null) builder.claim("planId", planId);
        if (roles != null && !roles.isEmpty()) builder.claim("roles", roles);

        return builder.compact();
    }

    /**
     * 認証情報からロール一覧を抽出する
     * 
     * @param authentication 認証情報
     * @return ロール一覧
     */
    public List<String> extractRoles(Authentication authentication) {
        if (authentication == null) return List.of();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a != null ? a.replaceFirst("^ROLE_", "") : "")
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * リフレッシュトークンの有効期限を取得
     * 
     * @return リフレッシュトークンの有効期限日時
     */
    public Date getRefreshTokenExpiryDate() {
        return new Date(System.currentTimeMillis() + refreshTokenExpiration);
    }

    /**
     * トークンからユーザーIDを取得
     * 
     * @param token JWTトークン
     * @return ユーザーID
     */
    public Integer getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        Object uid = claims.get("uid");
        if (uid instanceof Integer) return (Integer) uid;
        if (uid instanceof Long) return ((Long) uid).intValue();
        if (uid instanceof Number) return ((Number) uid).intValue();
        
        return null;
    }

    /**
     * トークンの subject（ユーザーID: user_id）を取得
     *
     * @param token JWTトークン
     * @return subject（user_id）
     */
    public String getUserIdStringFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    /**
     * 任意のクレームを取得（型は呼び出し側で解釈）
     *
     * @param token JWTトークン
     * @param claimName 取得したいクレームキー（例："plan_id"）
     * @return クレーム値。存在しない場合は null
     */
    public Object getClaim(String token, String claimName) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get(claimName);
    }

    /**
     * トークンの有効性を検証
     * 
     * @param token JWTトークン
     * @return 有効であればtrue、無効であればfalse
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);

            return true;

        } catch (ExpiredJwtException ex) {
            log.info("JWTが期限切れです");
        } catch (JwtException | IllegalArgumentException ex) {
            log.info("JWTが不正です");
        }

        return false;
    }

}
