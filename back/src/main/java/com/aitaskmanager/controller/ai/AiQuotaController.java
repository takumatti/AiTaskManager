package com.aitaskmanager.controller.ai;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
import org.springframework.beans.factory.annotation.Value;

import com.aitaskmanager.repository.customMapper.CustomAiUsageMapper;
import com.aitaskmanager.repository.customMapper.SubscriptionsCustomMapper;
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

    @Autowired
    private SubscriptionsCustomMapper subscriptionsCustomMapper;

    @Value("${openai.apiKey:}")
    private String openaiApiKey;

    /**
     * 現在のユーザーのAIクォータ情報を取得するエンドポイント
     *
     * @return クォータ情報を含むレスポンスエンティティ
     */
    @GetMapping("/quota")
    public ResponseEntity<Map<String, Object>> getQuota() {
        Authentication auth0 = SecurityContextHolder.getContext().getAuthentication();
        LogUtil.controller(AiQuotaController.class, "ai.quota", null, auth0 != null ? AuthUtils.getUserId(auth0) : null, "invoked");
        try {
            // OPENAI未設定時でもプラン情報は返せるよう、フラグのみ設定
            boolean aiConfigured = !(openaiApiKey == null || openaiApiKey.isBlank());

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("unauthorized"));
            }

            // 認証情報からuser_idとplan_id を取得
            String userId = AuthUtils.getUserId(auth);
            Integer planIdFromToken = AuthUtils.getPlanId(auth);

            Users user = (userId != null) ? userMapper.selectByUserId(userId) : null;
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("user-not-found"));
            }

            // plan_idはトークンにあれば優先、無ければDBの値
            String planResolve = null;
            Integer planId = (planIdFromToken != null) ? planIdFromToken : user.getPlanId();
            if (planIdFromToken != null) planResolve = "token"; else if (user.getPlanId() != null) planResolve = "db";
            SubscriptionPlans plan = planId != null ? subscriptionPlansMapper.selectByPrimaryKey(planId) : null;
            if (plan == null) {
                // フォールバック: 利用可能なプラン一覧からデフォルト（先頭）を選択
                try {
                    LogUtil.controller(AiQuotaController.class, "ai.quota", null, userId, "plan-fallback:select-all");
                    var all = subscriptionPlansMapper.selectAll();
                    if (all != null && !all.isEmpty()) {
                        plan = all.get(0);
                        planId = plan.getSubscriptionPlanSid();
                        planResolve = "fallback";
                    }
                } catch (Exception ignore) {
                    // 何もしない（後続でAI不可扱い）
                }
            }
            // プランが無い場合はAI不可扱い（0）
            Integer aiQuota = plan != null ? plan.getAiQuota() : 0; // null=unlimited, 0=not allowed

            // 今月のAI使用量を取得
            LocalDate now = LocalDate.now();
            Integer uid = (user.getUserSid() != null) ? Math.toIntExact(user.getUserSid()) : null;
                Integer usedCount = (uid != null) ? customAiUsageMapper.selectUsedCount(uid, now.getYear(), now.getMonthValue()) : 0;
                int used = (usedCount != null) ? usedCount.intValue() : 0;
                // ボーナス回数（回数パック）を取得
                Integer bonusCount = (uid != null) ? customAiUsageMapper.selectBonusCount(uid, now.getYear(), now.getMonthValue()) : 0;
                int bonus = (bonusCount != null) ? bonusCount.intValue() : 0;

            // リセット日: 契約開始日の翌月同日（存在しない場合は月末にクランプ）。契約が無い場合は翌月1日。
            LocalDate resetDate;
            long daysUntilReset;
            try {
                Long userSid = user.getUserSid();
                java.sql.Timestamp startedAtTs = (userSid != null) ? subscriptionsCustomMapper.selectLatestStartedAt(userSid) : null;
                if (startedAtTs != null) {
                    LocalDate startedDate = startedAtTs.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    int dom = startedDate.getDayOfMonth();
                    LocalDate cand = startedDate.plusMonths(1);
                    int endOfMonth = cand.lengthOfMonth();
                    int clampedDay = Math.min(dom, endOfMonth);
                    resetDate = LocalDate.of(cand.getYear(), cand.getMonth(), clampedDay);
                } else {
                    resetDate = now.withDayOfMonth(1).plusMonths(1);
                }
                daysUntilReset = Math.max(0, ChronoUnit.DAYS.between(now, resetDate));
            } catch (Exception ex0) {
                // フォールバック（安全策）
                resetDate = now.withDayOfMonth(1).plusMonths(1);
                daysUntilReset = Math.max(0, ChronoUnit.DAYS.between(now, resetDate));
            }

            // レスポンス作成
            Map<String, Object> body = new HashMap<>();
            body.put("planName", plan != null ? plan.getName() : "");
            body.put("planId", plan != null ? plan.getSubscriptionPlanSid() : null);
            body.put("planResolve", planResolve);
            boolean unlimited = (aiQuota == null);
            body.put("unlimited", unlimited);
            Integer remaining = unlimited ? null : Integer.valueOf(Math.max(0, aiQuota + bonus - used));
            body.put("remaining", remaining);
            body.put("aiConfigured", aiConfigured);
            body.put("resetDate", resetDate.toString()); // ISO形式 YYYY-MM-DD
            body.put("daysUntilReset", Long.valueOf(daysUntilReset));
            if (!aiConfigured) {
                body.put("message", "AI連携（OpenAI APIキー）が未設定です");
            }

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