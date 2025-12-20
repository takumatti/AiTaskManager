package com.aitaskmanager.controller.login;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
import com.aitaskmanager.util.LogUtil;

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
        // コントローラ入口ログ（email も許容するため userId は入力値をそのまま）
        LogUtil.controller(AuthController.class, "auth.login", null, request != null ? request.getUserId() : null, "invoked");
        // 入力は user_id または email を許容
        String loginIdOrEmail = request.getUserId();
        boolean isEmail = loginIdOrEmail != null && loginIdOrEmail.contains("@");

        // email の場合は対応する user_id に解決
        String resolvedUserId = loginIdOrEmail;
        Users user = null;
        if (isEmail) {
            user = userMapper.selectByEmail(loginIdOrEmail);
            if (user == null) {
                throw new BadCredentialsException("メールまたはパスワードが不正です");
            }
            resolvedUserId = user.getUserId();
        }

        // 認証（UserDetailsService は user_id でユーザをロード）
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        resolvedUserId,
                        request.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // ユーザー情報を user_id で再取得（email での一時取得では user_sid 等が欠ける可能性があるため）
        user = userMapper.selectByUserId(resolvedUserId);
        if (user == null) {
            throw new BadCredentialsException("ユーザーが見つかりません");
        }

        // ロール抽出
        List<String> roles = jwtTokenProvider.extractRoles(authentication);

        // JWT生成用の数値UID（users.user_sid）を使用
        Long userSid = user.getUserSid();
        Integer uid = (userSid != null) ? Math.toIntExact(userSid) : null;
        String accessToken = jwtTokenProvider.generateAccessToken(resolvedUserId, uid, roles);
        String refreshToken = jwtTokenProvider.generateRefreshToken(resolvedUserId, uid, roles);

        // RefreshToken 保存
        Date refreshTokenExpireAt = jwtTokenProvider.getRefreshTokenExpiryDate();
        refreshTokenService.saveRefreshToken(resolvedUserId, refreshToken, refreshTokenExpireAt);

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
        LogUtil.controller(AuthController.class, "auth.register", null, request != null ? request.getUserId() : null, "invoked");
        // ユーザIDの重複チェック（user_id は一意）
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ユーザーIDは必須です");
        }
        Users existing = userMapper.selectByUserId(request.getUserId());
        if (existing != null) {
            // 一意制約に抵触する前に明示的に弾く（409）
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ユーザーIDが既に使用されています");
        }
        // メール重複チェック
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "メールアドレスは必須です");
        }
        Users byEmail = userMapper.selectByEmail(request.getEmail());
        if (byEmail != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "メールアドレスが既に使用されています");
        }
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
        // トークンから uid と userId を抽出できる場合は入口ログ
        Integer uidForLog = null;
        String userIdForLog = null;
        try {
            if (request != null && request.getRefreshToken() != null) {
                uidForLog = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());
                userIdForLog = refreshTokenService.validateRefreshToken(request.getRefreshToken());
            }
        } catch (Exception ignored) { /* 検証は直後に本処理で実施 */ }
        LogUtil.controller(AuthController.class, "auth.refresh", uidForLog, userIdForLog, "invoked");
        // DB & JWT の検証
        String userId = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        Integer uid = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());

        // 新トークン発行（現在の認証情報からロール抽出）
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var roles = jwtTokenProvider.extractRoles(authentication);
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, uid, roles);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId, uid, roles);

        // RefreshToken の有効期限取得
        Date refreshTokenExpireAt = jwtTokenProvider.getRefreshTokenExpiryDate();

        // DB に保存（過去のトークンを削除し、新しいトークンを保存）
        refreshTokenService.saveRefreshToken(userId, newRefreshToken, refreshTokenExpireAt);

        return new LoginResponse(newAccessToken, newRefreshToken, uid);
    }

    /**
     * ログアウトエンドポイント 
     * 
     * @param request リフレッシュリクエストDTO
     */
    @PostMapping("/logout")
    public void logout(@RequestBody RefreshRequest request) {
        // JWT からユーザーID（文字列の subject）を取得
        String userId = jwtTokenProvider.getUserIdStringFromToken(request.getRefreshToken());
        Integer uid = null;
        try { uid = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken()); } catch (Exception ignored) {}
        LogUtil.controller(AuthController.class, "auth.logout", uid, userId, "invoked");
        // DB から削除
        refreshTokenService.deleteRefreshToken(userId);
    }

}
