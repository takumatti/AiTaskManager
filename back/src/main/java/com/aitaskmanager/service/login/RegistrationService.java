package com.aitaskmanager.service.login;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.dto.login.RegisterRequest;
import com.aitaskmanager.repository.dto.login.LoginResponse;
import com.aitaskmanager.repository.model.Users;
import com.aitaskmanager.security.JwtTokenProvider;
import com.aitaskmanager.util.LogUtil;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * ユーザー登録に関連するビジネスロジックを提供するサービス
 */
@Service
public class RegistrationService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenService refreshTokenService;

    /**
     * 新規ユーザーを登録する
     * 
     * @param request 登録リクエストDTO
     * @return ログインレスポンスDTO（登録後すぐログイン扱い）
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        LogUtil.service(RegistrationService.class, "auth.register", "userId=" + (request != null ? request.getUserId() : null), "started");
        // 基本バリデーション（userId/username/email/password）
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ユーザーIDは必須です");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ユーザー名は必須です");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "メールアドレスは必須です");
        }
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "パスワードは8文字以上にしてください");
        }
        String lowerUser = request.getUserId().toLowerCase();
        if (lowerUser.equals(request.getPassword().toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "パスワードをユーザーIDと同一にすることはできません");
        }

        // 重複チェック（userId と email）
        if (userMapper.selectByUserId(request.getUserId()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "そのユーザーIDは既に使用されています");
        }
        if (userMapper.selectByEmail(request.getEmail()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "そのメールアドレスは既に使用されています");
        }

        // 保存
        Users user = new Users();
        user.setUserId(request.getUserId());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        user.setPlanId(null);
        user.setIsActive(true);
        try {
            userMapper.insertUser(user);
        } catch (DataIntegrityViolationException e) {
            // DB側のユニーク制約違反などは409に変換
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ユーザーIDまたはメールアドレスが既に使用されています");
        }

        // 取得し直して内部数値IDとロールを確認
        Users saved = userMapper.selectByUserId(request.getUserId());
        Integer uid = (saved != null && saved.getUserSid() != null) ? Math.toIntExact(saved.getUserSid()) : null;

        // JWT発行 & Refresh保存（subject=user_id、uid=内部ID、rolesをクレームに含める）
        java.util.List<String> roles = (saved != null && saved.getRole() != null)
            ? java.util.List.of(saved.getRole())
            : java.util.List.of("USER");
        String accessToken = jwtTokenProvider.generateAccessToken(request.getUserId(), uid, roles);
        String refreshToken = jwtTokenProvider.generateRefreshToken(request.getUserId(), uid, roles);
        java.util.Date refreshTokenExpireAt = jwtTokenProvider.getRefreshTokenExpiryDate();
        refreshTokenService.saveRefreshToken(request.getUserId(), refreshToken, refreshTokenExpireAt);

        LogUtil.service(RegistrationService.class, "auth.register", "uid=" + uid, "completed");
        return new LoginResponse(accessToken, refreshToken, uid);
    }
}
