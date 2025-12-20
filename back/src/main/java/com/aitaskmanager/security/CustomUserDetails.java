package com.aitaskmanager.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * UserDetails
 */
public class CustomUserDetails implements UserDetails {
    private final String userId;
    private final String password;
    private final List<SimpleGrantedAuthority> authorities;
    private final Integer planId;
    private final Boolean isActive;

    /**
     * コンストラクタ
     * 
     * @param userId ユーザーID
     * @param password パスワード
     * @param role 役割
     * @param planId プランID
     * @param isActive 有効フラグ
     */
    public CustomUserDetails(String userId, String password, String role, Integer planId, Boolean isActive) {
        this.userId = userId;
        this.password = password;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        this.planId = planId;
        this.isActive = isActive != null ? isActive : Boolean.TRUE;
    }

    public Integer getPlanId() { return planId; }

    public Boolean getIsActive() { return isActive; }

    /**
     * ユーザーの権限を返す
     * 
     * @return 権限のコレクション
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * パスワードを返す
     * 
     * @return パスワード
     */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * ユーザーIDを返す
     * 
     * @return ユーザーID
     */
    @Override
    public String getUsername() {
        return userId;
    }

    /**
     * アカウントの有効期限が切れていないかを返す
     * 
     * @return trueなら有効期限切れでない
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * アカウントがロックされていないかを返す
     * 
     * @return trueならロックされていない
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 資格情報の有効期限が切れていないかを返す
     * 
     * @return trueなら有効期限切れでない
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * アカウントが有効かどうかを返す
     * 
     * @return trueなら有効
     */
    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(isActive);
    }
}
