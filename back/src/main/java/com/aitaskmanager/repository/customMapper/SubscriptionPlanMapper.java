package com.aitaskmanager.repository.customMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.aitaskmanager.repository.model.SubscriptionPlans;

/**
 * SubscriptionPlanテーブルに対するカスタムマッパー
 */
@Mapper
public interface SubscriptionPlanMapper {

    /**
     * プランIDを指定してサブスクリプションプランを取得する
     * @param id プランID
     * @return サブスクリプションプラン情報
     */
    SubscriptionPlans selectById(@Param("id") Integer id);
}
