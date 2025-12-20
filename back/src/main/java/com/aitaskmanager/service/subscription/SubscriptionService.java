package com.aitaskmanager.service.subscription;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.generator.SubscriptionPlansMapper;
import com.aitaskmanager.repository.model.SubscriptionPlans;
import com.aitaskmanager.repository.model.Users;
import com.aitaskmanager.util.LogUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * サブスクリプションに関連するビジネスロジックを提供するサービス
 */
@Service
@Slf4j
public class SubscriptionService {

    @Autowired
    private SubscriptionPlansMapper subscriptionPlansMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * すべてのプランを取得してクライアント向けの簡易マップへ整形
     * 
     * @return プランのリスト
     */
    public List<Map<String, Object>> getPlans() {
        LogUtil.service(SubscriptionService.class, "plans.list", "", "started");
        try {
            List<SubscriptionPlans> plans = subscriptionPlansMapper.selectAll();
            List<Map<String, Object>> body = plans.stream().map(p -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", p.getSubscriptionPlanSid());
                m.put("name", p.getName());
                m.put("aiQuota", p.getAiQuota());
                return m;
            }).toList();
            LogUtil.service(SubscriptionService.class, "plans.list", "count=" + body.size(), "completed");
            return body;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[Service] plans.list unexpected-error", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "プラン一覧の取得に失敗しました");
        }
    }

    /**
     * 現在ユーザーのプランを変更
     * @param userSid 内部数値ID（ログ用）
     * @param userId  文字列の user_id（更新キー）
     * @param planId  変更先プランID
     * @return レスポンス用マップ
     */
    public Map<String, Object> changePlan(Integer userSid, String userId, Integer planId) {
        LogUtil.service(SubscriptionService.class, "subscriptions.change", "userSid=" + userSid + " userId=" + userId + " planId=" + planId, "started");
        try {
            if (userId == null || userId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
            }
            Users user = userMapper.selectByUserId(userId);
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user-not-found");
            }
            if (planId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid-plan");
            }
            SubscriptionPlans plan = subscriptionPlansMapper.selectByPrimaryKey(planId);
            if (plan == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid-plan");
            }
            userMapper.updatePlanId(userId, planId);
            LogUtil.service(SubscriptionService.class, "subscriptions.change", "userSid=" + userSid + " planId=" + planId, "completed");
            return Map.of(
                "message", "ok",
                "planId", plan.getSubscriptionPlanSid(),
                "planName", plan.getName()
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[Service] subscriptions.change unexpected-error userSid={} userId={} planId={}", userSid, userId, planId, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "プラン変更に失敗しました");
        }
    }
}
