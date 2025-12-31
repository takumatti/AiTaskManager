package com.aitaskmanager.repository.customMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Subscriptionsテーブルに対するカスタムマッパー
 */
@Mapper
public interface SubscriptionsCustomMapper {

    /**
     * サブスクリプションを挿入する
     * 
     * @param userSid ユーザーSID
     * @param planSid プランSID
     * @param expiresAt 有効期限
     * @return 挿入された行数
     */
    int insertSubscription(@Param("userSid") int userSid,
                           @Param("planSid") int planSid,
                           @Param("expiresAt") java.sql.Timestamp expiresAt);

    /**
     * アクティブなサブスクリプションが存在するか確認する
     * 
     * @param userSid ユーザーSID
     * @param planSid プランSID
     * @return 存在する場合は1、存在しない場合は0
     */
    int hasActive(@Param("userSid") int userSid, @Param("planSid") int planSid);

    /**
     * ユーザーの最新のアクティブ契約の開始日を取得する
     *
     * @param userSid ユーザーSID
     * @return 最新の started_at（存在しない場合はNULL）
     */
    java.sql.Timestamp selectLatestStartedAt(@Param("userSid") long userSid);

    /**
     * 現在アクティブなサブスクリプションのplan_sidを取得する
     * @param userSid ユーザーSID
     * @return plan_sid（存在しない場合はNULL）
     */
    Integer selectActivePlanSid(@Param("userSid") long userSid);

    /**
     * ユーザーのACTIVEサブスクリプションをCANCELLEDへ更新する
     * @param userSid ユーザーSID
     * @return 更新件数
     */
    int cancelActiveByUserSid(@Param("userSid") int userSid);
}