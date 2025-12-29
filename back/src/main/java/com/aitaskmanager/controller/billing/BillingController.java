package com.aitaskmanager.controller.billing;

import com.aitaskmanager.service.billing.StripeBillingService;
import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.security.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import lombok.RequiredArgsConstructor;

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
            userMapper.updatePlanId(userId, planId);
            return ResponseEntity.ok().body(new MessageResponse("free-changed"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("failed-to-change-free"));
        }
    }

    public record MessageResponse(String message) {}
}
