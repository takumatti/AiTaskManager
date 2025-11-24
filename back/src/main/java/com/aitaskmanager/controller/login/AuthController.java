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

import com.aitaskmanager.repository.dto.login.LoginRequest;
import com.aitaskmanager.repository.dto.login.LoginResponse;
import com.aitaskmanager.repository.dto.login.RefreshRequest;
import com.aitaskmanager.security.JwtTokenProvider;
import com.aitaskmanager.service.login.RefreshTokenService;

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

    /**
     * ログインエンドポイント
     * 
     * @param request ログインリクエストDTO
     * @return ログインレスポンスDTO
     */
     @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {

        // Spring Security の認証処理
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // JWT生成
        String accessToken = jwtTokenProvider.generateAccessToken(request.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(request.getUsername());

        // RefreshToken の有効期限取得
        Date refreshTokenExpireAt = jwtTokenProvider.getRefreshTokenExpiryDate();

        // DB に保存（過去のトークンを削除し、新しいトークンを保存）
        refreshTokenService.saveRefreshToken(
                request.getUsername(),
                refreshToken,
                refreshTokenExpireAt
        );
        
        return new LoginResponse(accessToken, refreshToken);
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

        // 新トークン発行
        String newAccessToken = jwtTokenProvider.generateAccessToken(username);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);

        // RefreshToken の有効期限取得
        Date refreshTokenExpireAt = jwtTokenProvider.getRefreshTokenExpiryDate();

        // DB に保存（過去のトークンを削除し、新しいトークンを保存）
        refreshTokenService.saveRefreshToken(username, newRefreshToken, refreshTokenExpireAt);

        return new LoginResponse(newAccessToken, newRefreshToken);
    }

    /**
     * ログアウトエンドポイント 
     */
    @PostMapping("/logout")
    public void logout(@RequestBody RefreshRequest request) {

        // JWT からユーザー名を取得
        String username = jwtTokenProvider.getUsernameFromToken(request.getRefreshToken());

        // DB から削除
        refreshTokenService.deleteRefreshToken(username);
    }

}
