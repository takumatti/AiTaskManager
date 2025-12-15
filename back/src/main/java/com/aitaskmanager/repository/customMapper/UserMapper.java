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

    /**
     * メールアドレスでユーザ取得
     * @param email メール
     * @return ユーザ情報
     */
    Users selectByEmail(String email);

    /**
     * メールアドレスでユーザID取得
     * @param email メール
     * @return ユーザID
     */
    Integer selectIdByEmail(String email);

    /**
     * 新規ユーザ登録
     * @param user Users
     */
    void insertUser(Users user);

    /**
     * ユーザーIDでユーザ取得
     * @param id ユーザーID
     * @return ユーザ情報
     */
    Users selectById(Integer id);

    /** ユーザーのプランIDを更新 */
    void updatePlanId(@org.apache.ibatis.annotations.Param("userId") Integer userId,
                      @org.apache.ibatis.annotations.Param("planId") Integer planId);

}