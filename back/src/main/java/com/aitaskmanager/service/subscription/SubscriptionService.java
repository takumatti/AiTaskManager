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

}
