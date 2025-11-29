package com.aitaskmanager.repository.dto.login;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新規ユーザ登録用リクエストDTO
 */
@Data
public class RegisterRequest {
    /** ユーザー名 */
    @NotBlank(message = "ユーザー名は必須です")
    private String username;
    // 必須: 一意
    /** メールアドレス */
    @NotBlank(message = "メールアドレスは必須です")
    @Email(message = "メールアドレスの形式が不正です")
    private String email;   
    /** パスワード */
    @NotBlank(message = "パスワードは必須です")
    @Size(min = 8, message = "パスワードは8文字以上にしてください")
    private String password; 
}
