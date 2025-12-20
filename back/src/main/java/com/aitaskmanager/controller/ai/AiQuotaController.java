package com.aitaskmanager.controller.ai;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aitaskmanager.repository.customMapper.CustomAiUsageMapper;
import com.aitaskmanager.security.AuthUtils;
import com.aitaskmanager.repository.generator.SubscriptionPlansMapper;
import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.model.SubscriptionPlans;
import com.aitaskmanager.repository.model.Users;
import com.aitaskmanager.util.LogUtil;

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
    private SubscriptionPlansMapper subscriptionPlansMapper;

    @Autowired
    private CustomAiUsageMapper customAiUsageMapper;

    /**
     * 現在のユーザーのAIクォータ情報を取得するエンドポイント
     *
     * @return クォータ情報を含むレスポンスエンティティ
     */
    @GetMapping("/quota")
    public ResponseEntity<Map<String, Object>> getQuota() {
        Authentication auth0 = SecurityContextHolder.getContext().getAuthentication();
        LogUtil.controller(AiQuotaController.class, "ai.quota", null, auth0 != null ? auth0.getName() : null, "invoked");
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

            // 認証情報から user_id と（あれば）plan_id を取得
            String userId = auth.getName(); // subject に user_id
            Integer planIdFromToken = AuthUtils.getPlanId(auth);

            Users user = (userId != null) ? userMapper.selectByUserId(userId) : null;
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("user-not-found"));
            }

            // plan_idはトークンにあれば優先、無ければDBの値
            Integer planId = (planIdFromToken != null) ? planIdFromToken : user.getPlanId();
            SubscriptionPlans plan = planId != null ? subscriptionPlansMapper.selectByPrimaryKey(planId) : null;
            // プランが無い場合はAI不可扱い（0）
            Integer aiQuota = plan != null ? plan.getAiQuota() : 0; // null=unlimited, 0=not allowed

            // 今月のAI使用量を取得
            LocalDate now = LocalDate.now();
            Integer uid = (user.getUserSid() != null) ? Math.toIntExact(user.getUserSid()) : null;
            int used = customAiUsageMapper.selectUsedCount(uid, now.getYear(), now.getMonthValue());

            // レスポンス作成
            Map<String, Object> body = new HashMap<>();
            body.put("planName", plan != null ? plan.getName() : "");
            boolean unlimited = (aiQuota == null);
            body.put("unlimited", unlimited);
            Integer remaining = unlimited ? null : Integer.valueOf(Math.max(0, aiQuota - used));
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
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("message", msg);
        return resultMap;
    }
}