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
     * ユーザーSIDでサブスクリプションプランを更新する
     *
     * @param userSid ユーザーSID
     * @param planId プランID
     */
    int updatePlanIdBySid(@Param("userSid") Integer userSid,
                          @Param("planId") Integer planId);

}