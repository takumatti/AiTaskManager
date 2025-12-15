package com.aitaskmanager.controller.subscription;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aitaskmanager.controller.subscription.dto.ChangePlanRequest;
import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.generator.SubscriptionPlansMapper;
import com.aitaskmanager.repository.model.SubscriptionPlans;
import com.aitaskmanager.repository.model.Users;

import lombok.RequiredArgsConstructor;

/**
 * サブスクリプションおよびプランに関連するAPIエンドポイントを提供するコントローラー
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubscriptionController {

    @Autowired
    private SubscriptionPlansMapper subscriptionPlansMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 利用可能なサブスクリプションプランのリストを取得するエンドポイント
     *
     * @return プランのリストを含むレスポンスエンティティ
     */
    @GetMapping("/plans")
    public ResponseEntity<List<Map<String, Object>>> getPlans() {
        List<SubscriptionPlans> plans = subscriptionPlansMapper.selectAll();
        List<Map<String, Object>> body = plans.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("aiQuota", p.getAiQuota());
            return m;
        }).toList();
        return ResponseEntity.ok(body);
    }

    /**
     * 現在のユーザーのサブスクリプションプランを変更するエンドポイント
     *
     * @param req プラン変更リクエスト
     * @return 処理結果を含むレスポンスエンティティ
     */
    @PostMapping("/subscriptions/change")
    public ResponseEntity<Map<String, Object>> changePlan(@RequestBody ChangePlanRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "unauthorized"));
        }
        String username = auth.getName();
        Users user = userMapper.selectByUserName(username);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "user-not-found"));
        }
        Integer planId = req.getPlanId();
        // 簡易バリデーション: 存在するプランか
        SubscriptionPlans plan = planId != null ? subscriptionPlansMapper.selectByPrimaryKey(planId) : null;
        if (plan == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "invalid-plan"));
        }
        userMapper.updatePlanId(user.getId(), planId);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }
}
