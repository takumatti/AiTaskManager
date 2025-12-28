package com.aitaskmanager.controller.billing;

import com.aitaskmanager.service.billing.StripeBillingService;
import com.aitaskmanager.security.AuthUtils;
import org.springframework.http.ResponseEntity;
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

    private final StripeBillingService stripeBillingService;

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
     * セッションURLレスポンスDTO
     */
    public record SessionUrlResponse(String sessionUrl) {}

    /**
     * エラーレスポンスDTO
     */
    public record ErrorResponse(String message) {}
}
