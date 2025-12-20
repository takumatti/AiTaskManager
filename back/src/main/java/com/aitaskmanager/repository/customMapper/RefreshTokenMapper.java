package com.aitaskmanager.repository.customMapper;

import org.apache.ibatis.annotations.Mapper;

import com.aitaskmanager.repository.model.RefreshTokens;


/**
 * RefreshTokenテーブルに対するカスタムマッパー
 */
@Mapper
public interface RefreshTokenMapper {

    /**
     * ユーザーIDを指定してリフレッシュトークンを取得する
     * 
     * @param userSid ユーザーSID
     * @return リフレッシュトークン情報
     */
    RefreshTokens selectByUserSid(int userSid);

    /**
     * リフレッシュトークンを挿入する
     * 
     * @param refreshToken リフレッシュトークン情報
     * @return 挿入件数
     */
    int insert(RefreshTokens refreshToken);

    /**
     * ユーザーSIDを指定してリフレッシュトークンを削除する
     * 
     * @param userSid ユーザーSID
     * @return 削除件数
     */
    int deleteByUserSid(Integer userSid);

}