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
     * @param userId ユーザーID
     * @return リフレッシュトークン情報
     */
    RefreshTokens selectByUserId(int userId);

    /**
     * リフレッシュトークンを挿入する
     * 
     * @param refreshToken リフレッシュトークン情報
     * @return 挿入件数
     */
    int insert(RefreshTokens refreshToken);

    /**
     * ユーザーIDを指定してリフレッシュトークンを削除する
     * 
     * @param userId ユーザーID
     * @return 削除件数
     */
    int deleteByUserId(Integer userId);

}