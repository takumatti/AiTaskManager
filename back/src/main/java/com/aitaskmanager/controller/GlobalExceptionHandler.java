
package com.aitaskmanager.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * グローバル例外ハンドラークラス
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 認証失敗（BadCredentials）
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .body(Map.of("message", "ユーザー名またはパスワードが違います"));
    }

    // ユーザーが見つからない（loadUserByUsername）
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<?> handleUsernameNotFound(UsernameNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .body(Map.of("message", "ユーザーが見つかりません"));
    }

    // その他
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .body(Map.of("message", "サーバーエラーが発生しました"));
    }
}