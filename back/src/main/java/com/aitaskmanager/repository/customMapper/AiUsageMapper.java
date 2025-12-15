package com.aitaskmanager.repository.customMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AI利用回数に関連するデータベース操作を定義するマッパー
 */
@Mapper
public interface AiUsageMapper {
    
    /** 
     * 指定されたユーザーIDと年月に基づいて使用済みのAI利用回数を取得する
     * @param userId ユーザーID
     * @param year 対象年
     * @param month 対象月
     * @return 使用済みのAI利用回数
     */
    Integer selectUsedCount(@Param("userId") Integer userId, @Param("year") Integer year, @Param("month") Integer month);

    /**
     * 指定されたユーザーIDと年月に基づいてAI利用回数をインクリメントする
     * @param userId ユーザーID
     * @param year 対象年
     * @param month 対象月
     */
    void upsertIncrement(@Param("userId") Integer userId, @Param("year") Integer year, @Param("month") Integer month);
}
