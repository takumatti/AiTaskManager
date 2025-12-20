package com.aitaskmanager.controller.subscription;

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

import com.aitaskmanager.repository.dto.subscription.ChangePlanRequest;
import com.aitaskmanager.service.subscription.SubscriptionService;
import com.aitaskmanager.util.LogUtil;
import com.aitaskmanager.util.RequestGuard;

import lombok.RequiredArgsConstructor;

/**
 * サブスクリプションおよびプランに関連するAPIエンドポイントを提供するコントローラー
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubscriptionController {

    @Autowired
    private SubscriptionService subscriptionService;

    /**
     * 利用可能なサブスクリプションプランのリストを取得するエンドポイント
     *
     * @return プランのリストを含むレスポンスエンティティ
     */
    @GetMapping("/plans")
    public ResponseEntity<List<Map<String, Object>>> getPlans() {
        LogUtil.controller(SubscriptionController.class, "plans.list", null, null, "invoked");
        return ResponseEntity.ok(subscriptionService.getPlans());
    }

    /**
     * 現在のユーザーのサブスクリプションプランを変更するエンドポイント
     *
     * @param request プラン変更リクエスト
     * @return 処理結果を含むレスポンスエンティティ
     */
    @PostMapping("/subscriptions/change")
    public ResponseEntity<Map<String, Object>> changePlan(@RequestBody ChangePlanRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Integer userSid = RequestGuard.requireUserSid();
        String username = (auth != null) ? auth.getName() : null;
        LogUtil.controller(SubscriptionController.class, "subscriptions.change", userSid, username, "invoked");
        Map<String, Object> body = subscriptionService.changePlan(userSid, (username != null ? username : null), request != null ? request.getPlanId() : null);
        return ResponseEntity.ok(body);
    }
}
