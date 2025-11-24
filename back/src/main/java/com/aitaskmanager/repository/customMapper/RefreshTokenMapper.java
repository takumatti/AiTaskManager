package com.aitaskmanager.repository.customMapper;

import org.apache.ibatis.annotations.Mapper;

import com.aitaskmanager.repository.model.RefreshTokens;

@Mapper
/**
 * RefreshTokenテーブルに対するカスタムマッパー
 */
public interface RefreshTokenMapper {

    /**
     * ユーザーIDを指定してリフレッシュトークンを取得する
     * @param userId
     * @return リフレッシュトークン情報
     */
    RefreshTokens selectByUserId(int userId);

    /**
     * リフレッシュトークンを挿入する
     * @param refreshToken
     * @return 挿入件数
     */
    int insert(RefreshTokens refreshToken);

    /**
     * ユーザーIDを指定してリフレッシュトークンを削除する
     * @param userId   
     * @return 削除件数
     */
    int deleteByUserId(Integer userId);

}