package com.aitaskmanager.controller.ai;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aitaskmanager.repository.customMapper.AiUsageMapper;
import com.aitaskmanager.repository.customMapper.SubscriptionPlanMapper;
import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.model.SubscriptionPlans;
import com.aitaskmanager.repository.model.Users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AIクォータに関連するAPIエンドポイントを提供するコントローラー
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiQuotaController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SubscriptionPlanMapper subscriptionPlanMapper;

    @Autowired
    private AiUsageMapper aiUsageMapper;

    /**
     * 現在のユーザーのAIクォータ情報を取得するエンドポイント
     *
     * @return クォータ情報を含むレスポンスエンティティ
     */
    @GetMapping("/quota")
    public ResponseEntity<Map<String, Object>> getQuota() {
        try {
            // OPENAI未設定時は503でガイダンス返却
            String apiKey = System.getProperty("openai.apiKey", System.getenv("OPENAI_API_KEY"));
            if (apiKey == null || apiKey.isBlank()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(error("OPENAI_API_KEY not configured"));
            }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("unauthorized"));
            }
            String username = auth.getName();
            Users user = userMapper.selectByUserName(username);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("user-not-found"));
            }

            Integer planId = user.getPlanId();
            SubscriptionPlans plan = planId != null ? subscriptionPlanMapper.selectById(planId) : null;
            // プランが無い場合はAI不可扱い（0）
            Integer aiQuota = plan != null ? plan.getAiQuota() : 0; // null=unlimited, 0=not allowed

            LocalDate now = LocalDate.now();
            int used = aiUsageMapper.selectUsedCount(user.getId(), now.getYear(), now.getMonthValue());

            Map<String, Object> body = new HashMap<>();
            body.put("planName", plan != null ? plan.getName() : "");
            boolean unlimited = (aiQuota == null);
            Integer remaining = unlimited ? null : Integer.valueOf(Math.max(0, aiQuota - used));
            body.put("unlimited", unlimited);
            body.put("remaining", remaining);
            return ResponseEntity.ok(body);
        } catch (Exception ex) {
            log.warn("[AiQuotaController] failed: {}", ex.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("internal-error"));
        }
    }

    /**
     * エラーメッセージを含むマップを作成するユーティリティメソッド
     *
     * @param msg エラーメッセージ
     * @return エラーメッセージを含むマップ
     */
    private Map<String, Object> error(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("message", msg);
        return m;
    }
}