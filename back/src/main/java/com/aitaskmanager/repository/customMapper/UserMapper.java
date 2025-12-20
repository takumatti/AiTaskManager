package com.aitaskmanager.repository.customMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.aitaskmanager.repository.model.Users;


/**
 * Usersテーブルに対するカスタムマッパー
 */
@Mapper
public interface UserMapper {

    /**
     * ユーザーIDでユーザ取得
     * 
     * @param userId ユーザーID
     * @return ユーザ情報
     */
    Users selectByUserId(String userId);

    /**
     * メールアドレスでユーザ取得
     * 
     * @param email メール
     * @return ユーザ情報
     */
    Users selectByEmail(String email);

    /**
     * メールアドレスでユーザID取得
     * 
     * @param email メール
     * @return ユーザID
     */
    Integer selectIdByEmail(String email);

    /**
     * 新規ユーザ登録
     * 
     * @param user Users
     */
    void insertUser(Users user);

    /**
     * ユーザーのサブスクリプションプランを更新する
     * 
     * @param userId ユーザID
     * @param planId プランID
     */
    void updatePlanId(@Param("userId") String userId,
                      @Param("planId") Integer planId);

}