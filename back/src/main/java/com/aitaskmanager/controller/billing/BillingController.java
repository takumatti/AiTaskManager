package com.aitaskmanager.controller.billing;

import com.aitaskmanager.service.billing.StripeBillingService;
import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.customMapper.SubscriptionsCustomMapper;
import com.aitaskmanager.repository.customMapper.CustomAiUsageMapper;
import com.aitaskmanager.repository.generator.SubscriptionPlansMapper;
import com.aitaskmanager.repository.model.SubscriptionPlans;
import com.aitaskmanager.security.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 請求関連のコントローラクラス
 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    @Autowired
    private StripeBillingService stripeBillingService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SubscriptionsCustomMapper subscriptionsCustomMapper;

    @Autowired
    private CustomAiUsageMapper customAiUsageMapper;

    @Autowired
    private SubscriptionPlansMapper subscriptionPlansMapper;

    /**
     * Stripe Checkoutセッションを作成するエンドポイント
     * 
     * @param planId プランID
     * @param authentication 認証情報
     * @return セッションURLを含むレスポンスエンティティ
     */
    @PostMapping("/checkout-session")
    public ResponseEntity<?> createCheckoutSession(@RequestParam("planId") int planId,
                                                   Authentication authentication) {
        String userId = AuthUtils.getUserId(authentication);
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("invalid-user"));
        }
        try {
            var url = stripeBillingService.createCheckoutSessionForPlan(planId, userId);
            return ResponseEntity.ok().body(new SessionUrlResponse(url));
        } catch (IllegalArgumentException e) {
            log.warn("CheckoutSession validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("CheckoutSession creation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * クレジットパック用のStripe Checkoutセッションを作成するエンドポイント
     * クライアントからStripeのPrice IDを受け取り、サーバー側でパックサイズを確定する
     * 
     * @param priceId Stripe Price ID
     * @param authentication 認証情報
     * @return セッションURLを含むレスポンスエンティティ
     */
    @PostMapping("/checkout-credit")
    public ResponseEntity<?> createCreditCheckout(@RequestParam("priceId") String priceId,
                                                  Authentication authentication) {
        String userId = AuthUtils.getUserId(authentication);
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("invalid-user"));
        }
        try {
            var url = stripeBillingService.createCheckoutSessionForCredit(priceId, userId);
            return ResponseEntity.ok().body(new SessionUrlResponse(url));
        } catch (IllegalArgumentException e) {
            log.warn("Credit CheckoutSession validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Credit CheckoutSession creation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * セッションURLレスポンスDTO
     */
    public record SessionUrlResponse(String sessionUrl) {}

    /**
     * エラーレスポンスDTO
     */
    public record ErrorResponse(String message) {}

    /**
     * 無料プラン（plan_id=1）に変更するエンドポイント（Stripe遷移なし）。
     * 注意: 表示/計算上はアクティブな契約（subscriptions）が優先されるため、
     * 今月購入済みのプランはリセットまで反映され続け、次回から無料が適用されます。
     * 
     * @param planId 変更先プランID（必ず1を指定）
     * @param authentication 認証情報
     * @return レスポンスエンティティ
     */
    @PostMapping("/change-to-free")
    @Transactional
    public ResponseEntity<?> changeToFree(@RequestParam("planId") int planId,
                                          Authentication authentication) {
        String userId = AuthUtils.getUserId(authentication);
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("invalid-user"));
        }
        if (planId != 1) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("only-free-allowed"));
        }
        try {
            // 1) ユーザーSID取得
            var user = userMapper.selectByUserId(userId);
            if (user == null || user.getUserSid() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("invalid-user"));
            }
            int userSid = user.getUserSid().intValue();

            // 2) 現在のACTIVE契約があるか確認（最新）
            Integer activePlanSid = subscriptionsCustomMapper.selectActivePlanSid(userSid);

            // 3) 未消化分をボーナスへロール
            if (activePlanSid != null) {
                SubscriptionPlans plan = subscriptionPlansMapper.selectByPrimaryKey(activePlanSid);
                Integer aiQuota = plan != null ? plan.getAiQuota() : null;
                boolean isUnlimited = aiQuota == null || (aiQuota != null && aiQuota == 4);
                // 当月の使用/ボーナスを取得（Unlimited/有料いずれも参照）
                java.time.LocalDate today = java.time.LocalDate.now();
                int year = today.getYear();
                int month = today.getMonthValue();
                Integer used = customAiUsageMapper.selectUsedCount(userSid, year, month);
                int usedCount = used != null ? used : 0;
                // 既存ボーナスはUnlimited→Free の分岐では利用しない（FREE_BASELINEを常に付与する方針）

                if (!isUnlimited) {
                    // 有料（上限あり）→ Free: 未消化分をボーナスへ加算
                    int quota = aiQuota != null ? aiQuota : 0;
                    int remainingPaid = Math.max(0, quota - usedCount);
                    if (remainingPaid > 0) {
                        customAiUsageMapper.upsertAddBonus(userSid, year, month, remainingPaid);
                    }
                } else {
                    // Unlimited → Free: Free分の基準 450 を「ボーナス」として付与する（既存クレジットに上乗せ）
                    // これにより remaining = (ai_quota[0] + bonus[既存+450]) - used = (既存クレジット + 450) - used
                    final int FREE_BASELINE = 450;
                    // 二重実行防止: 本APIはアクティブ契約がある時のみこの分岐に入るため、通常は1回のみ。安全側で必要最小加算を行う。
                    int delta = Math.max(0, FREE_BASELINE);
                    if (delta > 0) {
                        customAiUsageMapper.upsertAddBonus(userSid, year, month, delta);
                    }
                }
                // ACTIVE契約はCANCELLEDへ（当月以降は使わない）
                subscriptionsCustomMapper.cancelActiveByUserSid(userSid);
            }

            // 4) users.plan_id を Free に更新
            userMapper.updatePlanIdBySid(userSid, planId);

            return ResponseEntity.ok().body(new MessageResponse("free-changed"));
        } catch (Exception e) {
            log.error("change-to-free failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("failed-to-change-free"));
        }
    }

    public record MessageResponse(String message) {}
}
