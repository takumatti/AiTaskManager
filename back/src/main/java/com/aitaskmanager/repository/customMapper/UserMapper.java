package com.aitaskmanager.repository.customMapper;

import org.apache.ibatis.annotations.Mapper;

import com.aitaskmanager.repository.model.Users;


/**
 * Usersテーブルに対するカスタムマッパー
 */
@Mapper
public interface UserMapper {

    /**
     * ユーザー名を指定してユーザーを取得する
     * @param username ユーザー名
     * @return  ユーザー情報
     */
    Users selectByUserName(String username);

    /**
     * ユーザー名を指定してユーザーIDを取得する
     * @param username ユーザー名
     * @return ユーザーID
     */
    Integer selectIdByUsername(String username);

}