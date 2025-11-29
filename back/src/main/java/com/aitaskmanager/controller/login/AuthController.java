package com.aitaskmanager.controller.login;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import com.aitaskmanager.repository.dto.login.LoginRequest;
import com.aitaskmanager.repository.dto.login.LoginResponse;
import com.aitaskmanager.repository.dto.login.RefreshRequest;
import com.aitaskmanager.repository.dto.login.RegisterRequest;
import com.aitaskmanager.security.JwtTokenProvider;
import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.model.Users;
import com.aitaskmanager.service.login.RefreshTokenService;
import com.aitaskmanager.service.login.RegistrationService;

/**
 * 認証関連のコントローラクラス
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RegistrationService registrationService;

    /**
     * ログインエンドポイント
     * 
     * @param request ログインリクエストDTO
     * @return ログインレスポンスDTO
     */
     @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {

        String rawId = request.getUsername(); // username 兼 email
        boolean isEmail = rawId != null && rawId.contains("@");

        // emailなら対応するusernameへ解決
        String resolvedUsername = rawId;
        Users user = null;
        if (isEmail) {
            user = userMapper.selectByEmail(rawId);
            if (user == null) {
                throw new org.springframework.security.authentication.BadCredentialsException("メールまたはパスワードが不正です");
            }
            resolvedUsername = user.getUsername();
        }

        // Spring Security の認証処理（UserDetailsServiceはusernameでロードされる前提）
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        resolvedUsername,
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // ユーザーID取得（username で再取得。emailログイン時は既に user がある）
        if (user == null) {
            user = userMapper.selectByUserName(resolvedUsername);
        }
        Integer uid = user != null ? user.getId() : null;

        // JWT生成（uidをクレームに含める）
        String accessToken = jwtTokenProvider.generateAccessToken(resolvedUsername, uid);
        String refreshToken = jwtTokenProvider.generateRefreshToken(resolvedUsername, uid);

        // RefreshToken の有効期限取得
        Date refreshTokenExpireAt = jwtTokenProvider.getRefreshTokenExpiryDate();

        // DB に保存（過去のトークンを削除し、新しいトークンを保存）
    refreshTokenService.saveRefreshToken(
        resolvedUsername,
        refreshToken,
        refreshTokenExpireAt
    );
        
    return new LoginResponse(accessToken, refreshToken, uid);
    }

    /**
     * 新規登録エンドポイント
     * 
     * @param request RegisterRequest
     * @return LoginResponse（登録後すぐログイン扱い）
     */
    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }

    /**
     * トークンリフレッシュエンドポイント
     * 
     * @param request リフレッシュリクエストDTO
     * @return ログインレスポンスDTO（新しいトークン）
     */
    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestBody RefreshRequest request) {
        
        // DB & JWT の検証
        String username = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        Integer uid = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());

        // 新トークン発行
        String newAccessToken = jwtTokenProvider.generateAccessToken(username, uid);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(username, uid);

        // RefreshToken の有効期限取得
        Date refreshTokenExpireAt = jwtTokenProvider.getRefreshTokenExpiryDate();

        // DB に保存（過去のトークンを削除し、新しいトークンを保存）
        refreshTokenService.saveRefreshToken(username, newRefreshToken, refreshTokenExpireAt);

        return new LoginResponse(newAccessToken, newRefreshToken, uid);
    }

    /**
     * ログアウトエンドポイント 
     * 
     * @param request リフレッシュリクエストDTO
     */
    @PostMapping("/logout")
    public void logout(@RequestBody RefreshRequest request) {

        // JWT からユーザー名を取得
        String username = jwtTokenProvider.getUsernameFromToken(request.getRefreshToken());

        // DB から削除
        refreshTokenService.deleteRefreshToken(username);
    }

}
