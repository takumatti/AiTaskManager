package com.aitaskmanager.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.model.Users;

/**
 * Spring Securityがログイン時に呼び出すユーザー情報取得クラス
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {
    
    @Autowired
    private UserMapper userMapper;

    /**
     * ユーザー名からユーザー情報を取得してUserDetailsに変換
     * 
     * @param userId ユーザーID
     * @return UserDetailsオブジェクト
     */
    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        Users user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw new UsernameNotFoundException("ユーザーが見つかりません: " + userId);
        }

        // Security用のUserDetailsに変換
        return new CustomUserDetails(
                userId,
                user.getPassword(),
                user.getRole(),
                user.getPlanId(),
                user.getIsActive()
        );
    }
    
}
