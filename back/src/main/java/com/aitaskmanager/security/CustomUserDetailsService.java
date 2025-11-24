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

    @Override
    /**
     * ユーザー名からユーザー情報を取得してUserDetailsに変換
     * 
     * @param username ユーザー名
     * @return UserDetailsオブジェクト
     */
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Users user = userMapper.selectByUserName(username);
        if (user == null) {
            throw new UsernameNotFoundException("ユーザーが見つかりません: " + username);
        }

        // Security用のUserDetailsに変換
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole())
                .build();
    }
    
}
