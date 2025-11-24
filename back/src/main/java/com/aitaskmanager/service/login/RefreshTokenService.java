package com.aitaskmanager.service.login;

import java.util.Date;

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
     * @param username ユーザー名
     * @param token リフレッシュトークン
     * @param expiresAt 有効期限
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRefreshToken(String username, String token, Date expiresAt) {

        Users user = userMapper.selectByUserName(username);

        if (user == null) {
            throw new UsernameNotFoundException("ユーザーが見つかりません");
        }

        // 古いトークン削除
        refreshTokenMapper.deleteByUserId(user.getId());

        // 新しいトークン保存
        RefreshTokens refreshToken = new RefreshTokens();
        refreshToken.setUserId(user.getId());
        refreshToken.setToken(token);
        refreshToken.setExpiresAt(expiresAt);

        refreshTokenMapper.insert(refreshToken);
    }

    /**
     * リフレッシュトークンの検証を行う
     * 
     * @param refreshToken リフレッシュトークン
     * @return ユーザー名
     */
    public String validateRefreshToken(String refreshToken) {
        // 1. JWTとして有効か確認
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadCredentialsException("リフレッシュトークンが不正または期限切れです");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        Users user = userMapper.selectByUserName(username);

        if (user == null) {
            throw new UsernameNotFoundException("トークンに紐づくユーザーが存在しません");
        }

        // 2. DBに保存されたトークンを取得
        RefreshTokens savedToken = refreshTokenMapper.selectByUserId(user.getId());

        if (savedToken == null) {
            throw new BadCredentialsException("リフレッシュトークンが登録されていません");
        }

        // 3. トークン一致チェック
        if (!savedToken.getToken().equals(refreshToken)) {
            throw new BadCredentialsException("リフレッシュトークンが一致しません");
        }

        // 4. 有効期限チェック（DB側）
        if (savedToken.getExpiresAt().before(new Date())) {
            throw new BadCredentialsException("リフレッシュトークンが期限切れです");
        }

        return username;
    }

    /**
     * 指定ユーザーのリフレッシュトークンを削除する
     * 
     * @param username ユーザー名
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRefreshToken(String username) {

        Users user = userMapper.selectByUserName(username);

        if (user == null) {
            throw new UsernameNotFoundException("ログアウト処理に失敗しました。ユーザーが存在しません。username=" + username);
        }

        refreshTokenMapper.deleteByUserId(user.getId());
    }

}
