package com.aitaskmanager.repository.dto.login;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新規ユーザ登録用リクエストDTO
 */
@Data
public class RegisterRequest {
    /** ログインID */
    @NotBlank(message = "ユーザーIDは必須です")
    @Size(min = 3, max = 32, message = "ユーザーIDは3〜32文字で入力してください")
    @Pattern(regexp = "^[A-Za-z0-9_\\-]+$", message = "ユーザーIDは英数字と-_のみ使用できます")
    private String userId;
    /** ユーザー名 */
    @NotBlank(message = "ユーザー名は必須です")
    @Size(min = 1, max = 50, message = "ユーザー名は1〜50文字で入力してください")
    private String username;
    /** メールアドレス */
    @NotBlank(message = "メールアドレスは必須です")
    @Email(message = "メールアドレスの形式が不正です")
    private String email;   
    /** パスワード */
    @NotBlank(message = "パスワードは必須です")
    @Size(min = 8, max = 128, message = "パスワードは8〜128文字で入力してください")
    private String password; 
}
