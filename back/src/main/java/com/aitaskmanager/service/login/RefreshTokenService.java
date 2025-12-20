package com.aitaskmanager.service.login;

import java.util.Date;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aitaskmanager.repository.customMapper.RefreshTokenMapper;
import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.model.RefreshTokens;
import com.aitaskmanager.repository.model.Users;
import com.aitaskmanager.security.JwtTokenProvider;
import com.aitaskmanager.util.LogUtil;

/**
 * リフレッシュトークンに関連するビジネスロジックを提供するサービス
 */
@Service
public class RefreshTokenService {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private RefreshTokenMapper refreshTokenMapper;
    
    /**
     * リフレッシュトークンを保存する
     * 
     * @param userId ユーザーId
     * @param token リフレッシュトークン
     * @param expiresAt 有効期限
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRefreshToken(String userId, String token, Date expiresAt) {
        LogUtil.service(RefreshTokenService.class, "auth.refresh-token.save", "userId=" + userId, "started");
        Users user = (userId != null) ? userMapper.selectByUserId(userId) : null;

        if (user == null) {
            throw new UsernameNotFoundException("ユーザーが見つかりません");
        }

        // 古いトークン削除
        Integer uid = (user.getUserSid() != null) ? Math.toIntExact(user.getUserSid()) : null;
        refreshTokenMapper.deleteByUserId(uid);

        // 新しいトークン保存（ハッシュ化）
        RefreshTokens refreshToken = new RefreshTokens();
        refreshToken.setUserSid(uid);
        refreshToken.setToken(hashToken(token));
        refreshToken.setExpiresAt(expiresAt);

        refreshTokenMapper.insert(refreshToken);
        LogUtil.service(RefreshTokenService.class, "auth.refresh-token.save", "uid=" + uid, "completed");
    }

    /**
     * リフレッシュトークンの検証を行う
     * 
     * @param refreshToken リフレッシュトークン
     * @return ユーザー名
     */
    public String validateRefreshToken(String refreshToken) {
        LogUtil.service(RefreshTokenService.class, "auth.refresh-token.validate", "tokenPresent=" + (refreshToken != null), "started");
        // 1. JWTとして有効か確認
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadCredentialsException("リフレッシュトークンが不正または期限切れです");
        }

        // subject は user_id（文字列）
        String userId = jwtTokenProvider.getUserIdStringFromToken(refreshToken);
        Users user = (userId != null) ? userMapper.selectByUserId(userId) : null;

        if (user == null) {
            throw new UsernameNotFoundException("トークンに紐づくユーザーが存在しません");
        }

        // 2. DBに保存されたトークンを取得（内部数値ID user_sid をキー）
        Integer uid = (user.getUserSid() != null) ? Math.toIntExact(user.getUserSid()) : null;
        RefreshTokens savedToken = refreshTokenMapper.selectByUserId(uid);

        if (savedToken == null) {
            throw new BadCredentialsException("リフレッシュトークンが登録されていません");
        }

        // 3. トークン一致チェック（ハッシュ比較）
        if (!savedToken.getToken().equals(hashToken(refreshToken))) {
            throw new BadCredentialsException("リフレッシュトークンが一致しません");
        }

        // 4. 有効期限チェック（DB側）
        if (savedToken.getExpiresAt().before(new Date())) {
            throw new BadCredentialsException("リフレッシュトークンが期限切れです");
        }

        LogUtil.service(RefreshTokenService.class, "auth.refresh-token.validate", "userId=" + userId + " uid=" + uid, "completed");
        return userId;
    }

    /**
     * 指定ユーザーのリフレッシュトークンを削除する
     * 
     * @param userId ユーザーId
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRefreshToken(String userId) {
        LogUtil.service(RefreshTokenService.class, "auth.refresh-token.delete", "userId=" + userId, "started");
        Users user = (userId != null) ? userMapper.selectByUserId(userId) : null;

        if (user == null) {
            throw new UsernameNotFoundException("ログアウト処理に失敗しました。ユーザーが存在しません。userId=" + userId);
        }

        Integer uid = (user.getUserSid() != null) ? Math.toIntExact(user.getUserSid()) : null;
        refreshTokenMapper.deleteByUserId(uid);
        LogUtil.service(RefreshTokenService.class, "auth.refresh-token.delete", "uid=" + uid, "completed");
    }

    /**
     * トークンハッシュ（SHA-256）を生成する
     * 
     * @param token 平文トークン
     * @return 16進文字列ハッシュ
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("ハッシュアルゴリズムが利用不可です", e);
        }
    }

}
