package com.aitaskmanager.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * グローバル例外ハンドラークラス
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 認証失敗時の例外ハンドラー
     * 
     * @param ex 例外オブジェクト
     * @return レスポンスエンティティ
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .body(Map.of("message", "ユーザー名またはパスワードが違います"));
    }

    /**
     * ユーザー未発見時の例外ハンドラー
     * 
     * @param ex 例外オブジェクト
     * @return レスポンスエンティティ
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<?> handleUsernameNotFound(UsernameNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .body(Map.of("message", "ユーザーが見つかりません"));
    }

    /**
     * 明示的なステータス付きの例外は、その reason をそのまま返す。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(status)
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .body(Map.of("message", message));
    }

    /**
     * その他の例外ハンドラー
     * 
     * @param ex 例外オブジェクト
     * @return レスポンスエンティティ
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .body(Map.of("message", "サーバーエラーが発生しました"));
    }

    /**
     * Bean Validation 例外（@Valid/@NotBlank 等）のハンドリング
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("入力値が不正です");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .body(Map.of("message", message));
    }
}