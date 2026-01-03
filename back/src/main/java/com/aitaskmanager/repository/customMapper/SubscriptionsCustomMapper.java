package com.aitaskmanager.repository.customMapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

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
                           @Param("expiresAt") Timestamp expiresAt,
                           @Param("stripeSubscriptionId") String stripeSubscriptionId);

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
     * 
     * @param userSid ユーザーSID
     * @return plan_sid（存在しない場合はNULL）
     */
    Integer selectActivePlanSid(@Param("userSid") long userSid);

    /**
     * 現在アクティブなサブスクリプションのStripe購読IDを取得する
     * 
     * @param userSid ユーザーSID
     * @return stripe_subscription_id（存在しない場合はNULL）
     */
    String selectActiveStripeSubscriptionId(@Param("userSid") long userSid);

    /**
     * Stripe購読IDで expires_at を更新する（cancel_at_period_end=true を受けた場合など）
     */
    int updateExpiresAtByStripeId(@Param("stripeSubscriptionId") String stripeSubscriptionId,
                                  @Param("expiresAt") java.sql.Timestamp expiresAt);

    /**
     * Stripe購読IDでCANCELLEDへ更新する（購読が終了/削除された場合）
     */
    int cancelByStripeId(@Param("stripeSubscriptionId") String stripeSubscriptionId,
                         @Param("canceledAt") java.sql.Timestamp canceledAt);

    /**
     * リコンシリエーション用: Stripe購読IDを持つACTIVE契約の一覧を取得
     */
    List<Map<String, Object>> selectActiveWithStripeId();

    /**
     * ユーザーのACTIVEサブスクリプションをCANCELLEDへ更新する
     * 
     * @param userSid ユーザーSID
     * @return 更新件数
     */
    int cancelActiveByUserSid(@Param("userSid") int userSid);
}