package com.aitaskmanager.controller.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripeウェブフック受信用コントローラー
 */
@RestController
@RequestMapping("/webhook/stripe")
public class WebhookController {
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    @Value("${stripe.webhookSecret:}")
    private String webhookSecret;

    /**
     * Stripeウェブフック受信エンドポイント
     *
     * @param payload   リクエストボディ
     * @param sigHeader Stripe-Signatureヘッダー
     * @return レスポンスエンティティ
     */
    @PostMapping
    public ResponseEntity<String> receive(@RequestBody String payload,
                                          @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        if (sigHeader == null || sigHeader.isBlank()) {
            log.warn("Stripe webhook missing signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("missing-signature");
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Stripe webhookSecret not configured");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("webhook-secret-not-configured");
        }

        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            String type = event.getType();
            log.info("[StripeWebhook] event received: {}", type);

            switch (type) {
                case "checkout.session.completed" -> {
                    // セッションオブジェクトにキャストしてメタデータ活用
                    Session session = (Session) event.getDataObjectDeserializer()
                            .getObject()
                            .orElse(null);
                    if (session != null) {
                        String userId = session.getMetadata() != null ? session.getMetadata().get("userId") : null;
                        String planId = session.getMetadata() != null ? session.getMetadata().get("planId") : null;
                        log.info("[StripeWebhook] checkout.session.completed userId={} planId={} sessionId={} amount_total={} currency={} payment_status={}",
                                userId, planId, session.getId(), session.getAmountTotal(), session.getCurrency(), session.getPaymentStatus());
                        // TODO: ここでpayments/subscriptions/ai_usageのDB更新を行う（冪等化）
                    }
                }
                default -> {
                    // 必要に応じて他イベントもハンドリング
                    log.info("[StripeWebhook] event ignored: {}", type);
                }
            }
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException e) {
            log.warn("[StripeWebhook] signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid-signature");
        } catch (Exception e) {
            log.error("[StripeWebhook] handler error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");
        }
    }
}
