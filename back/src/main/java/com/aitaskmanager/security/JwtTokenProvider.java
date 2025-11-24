package com.aitaskmanager.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

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
     * アクセストークンを生成
     * 
     * @param username ユーザー名
     * @return 生成されたアクセストークン
     */
    public String generateAccessToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * リフレッシュトークンを生成
     * 
     * @param username ユーザー名
     * @return 生成されたリフレッシュトークン
     */
    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
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
     * トークンからユーザー名を取得
     * 
     * @param token JWTトークン
     * @return ユーザー名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
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
