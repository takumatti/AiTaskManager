package com.aitaskmanager.repository.customMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AI利用回数に関連するデータベース操作を定義するマッパー
 */
@Mapper
public interface CustomAiUsageMapper {
    
    /** 
     * 指定されたユーザーSIDと年月に基づいて使用済みのAI利用回数を取得する
     * 
     * @param userSid ユーザーSID
     * @param year 対象年
     * @param month 対象月
     * @return 使用済みのAI利用回数
     */
    Integer selectUsedCount(@Param("userSid") Integer userSid,
                            @Param("year") Integer year,
                            @Param("month") Integer month);

    /**
     * 指定されたユーザーSIDと年月に基づいてAI利用回数をインクリメントする
     * 
     * @param userSid ユーザーSID
     * @param year 対象年
     * @param month 対象月
     */
    void upsertIncrement(@Param("userSid") Integer userSid,
                         @Param("year") Integer year,
                         @Param("month") Integer month);

    /**
     * 指定されたユーザーSIDと年月のボーナス回数（回数パック）を取得する
     *
     * @param userSid ユーザーSID
     * @param year 対象年
     * @param month 対象月
     * @return ボーナス回数（存在しない場合はNULL）
     */
    Integer selectBonusCount(@Param("userSid") Integer userSid,
                             @Param("year") Integer year,
                             @Param("month") Integer month);

    /**
     * 指定されたユーザーSIDと年月のボーナス回数（回数パック）を加算する（行がなければINSERT）
     *
     * @param userSid ユーザーSID
     * @param year 対象年
     * @param month 対象月
     * @param amount 追加する回数
     */
    void upsertAddBonus(@Param("userSid") Integer userSid,
                        @Param("year") Integer year,
                        @Param("month") Integer month,
                        @Param("amount") Integer amount);
}
