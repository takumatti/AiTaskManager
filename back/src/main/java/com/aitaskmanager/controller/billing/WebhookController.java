package com.aitaskmanager.controller.billing;

import com.aitaskmanager.service.billing.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.net.ApiResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import com.aitaskmanager.repository.customMapper.UserMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Autowired
    private StripeWebhookService stripeWebhookService;

    @Autowired
    private UserMapper userMapper;

    /**
     * Stripeのウェブフックイベントを受信するエンドポイント
     * 
        * @param payload リクエストボディ（ペイロード）
        * @param sigHeader Stripe-Signatureヘッダ
        * @return レスポンスエンティティ
     */
    @PostMapping
    public ResponseEntity<String> receive(@RequestBody(required = false) String payload,
                                          @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        // entry diagnostics
        int bodyLen = payload != null ? payload.length() : 0;
        log.info("[StripeWebhook] 受信開始: 署名ヘッダ有無={} ペイロード長={} シークレット設定有無={}",
            (sigHeader != null && !sigHeader.isBlank()), bodyLen, (webhookSecret != null && !webhookSecret.isBlank()));
        if (sigHeader == null || sigHeader.isBlank()) {
            log.warn("[StripeWebhook] 早期終了: 署名ヘッダがありません");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("missing-signature");
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("[StripeWebhook] 早期終了: webhookSecret が未設定です");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("webhook-secret-not-configured");
        }
        if (payload == null || payload.isBlank()) {
            log.warn("[StripeWebhook] 早期終了: ペイロードが空です");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("empty-payload");
        }

        try {
            log.debug("[StripeWebhook] イベントオブジェクトを構築中...");
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            String type = event.getType();
            String eventId = event.getId();
            log.info("[StripeWebhook] イベント受信: 種類={} イベントID={}", type, eventId);

            switch (type) {
                case "checkout.session.completed" -> {
                    // セッションオブジェクトにキャストしてメタデータ活用
                    var deser = event.getDataObjectDeserializer();
                    Session session = (Session) deser.getObject().orElse(null);
                    if (session == null) {
                        String rawJson = deser.getRawJson();
                        log.warn("[StripeWebhook] セッションのデシリアライズ結果がnullです。rawJson有無={} rawJson={}",
                                rawJson != null, rawJson);
                        // フォールバック: rawJson から Session を再構築
                        if (rawJson != null && !rawJson.isBlank()) {
                            try {
                                session = ApiResource.GSON.fromJson(rawJson, Session.class);
                                log.info("[StripeWebhook] フォールバックでSessionを再構築しました: sessionId={}",
                                        session != null ? session.getId() : null);
                            } catch (Exception jex) {
                                log.error("[StripeWebhook] rawJsonからのSession再構築に失敗: {}", jex.getMessage(), jex);
                            }
                        }
                    }
                    if (session != null) {
                        String userIdStr = session.getMetadata() != null ? session.getMetadata().get("userId") : null;
                        String planIdStr = session.getMetadata() != null ? session.getMetadata().get("planId") : null;
                        String purchaseType = session.getMetadata() != null ? session.getMetadata().get("type") : null; // 'credit_pack' or null
                        String creditAmountStr = session.getMetadata() != null ? session.getMetadata().get("amount") : null; // e.g., '10'
                        log.info("[StripeWebhook] checkout.session.completed メタ情報: userId={} planId={} type={} amount={} sessionId={} amount_total={} currency={} payment_status={}",
                                userIdStr, planIdStr, purchaseType, creditAmountStr, session.getId(), session.getAmountTotal(), session.getCurrency(), session.getPaymentStatus());

                        try {
                            if (userIdStr == null) {
                                log.warn("[StripeWebhook] メタデータに userId がありません。metadata={}", session.getMetadata());
                                break;
                            }
                            int userSid;
                            try {
                                userSid = Integer.parseInt(userIdStr);
                            } catch (NumberFormatException nfe) {
                                // フォールバック: user_id 文字列から user_sid を取得
                                try {
                                    var user = userMapper.selectByUserId(userIdStr);
                                    if (user == null) {
                                        log.warn("[StripeWebhook] userId='{}' に該当するユーザーが見つかりません。イベントをスキップします。", userIdStr);
                                        break;
                                    }
                                    Long userSidLong = user.getUserSid();
                                    if (userSidLong == null) {
                                        log.warn("[StripeWebhook] userId='{}' の user_sid がNULLです。イベントをスキップします。", userIdStr);
                                        break;
                                    }
                                    if (userSidLong > Integer.MAX_VALUE) {
                                        log.warn("[StripeWebhook] user_sid がintの範囲外です ({}). イベントをスキップします。", userSidLong);
                                        break;
                                    }
                                    userSid = userSidLong.intValue();
                                    log.info("[StripeWebhook] フォールバックにより userSid を解決: userId='{}' -> userSid={}", userIdStr, userSid);
                                } catch (Exception qex) {
                                    log.error("[StripeWebhook] userId フォールバック解決中にエラー: {}", qex.getMessage(), qex);
                                    break;
                                }
                            }
                            // クレジットパックの場合
                            if (purchaseType != null && purchaseType.equalsIgnoreCase("credit_pack")) {
                                if (creditAmountStr == null) {
                                    log.warn("[StripeWebhook] credit_pack の amount がメタデータにありません。metadata={}", session.getMetadata());
                                    break;
                                }
                                int creditAmount;
                                try {
                                    creditAmount = Integer.parseInt(creditAmountStr);
                                } catch (NumberFormatException nfe) {
                                    log.warn("[StripeWebhook] credit_pack の amount が数値ではありません。値='{}'. スキップします。", creditAmountStr);
                                    break;
                                }
                                log.debug("[StripeWebhook] クレジットパック永続化へ委譲: userSid={} amount={} sessionId={}", userSid, creditAmount, session.getId());
                                stripeWebhookService.persistCreditPack(userSid, creditAmount, session);
                                log.info("[StripeWebhook] クレジットパック永続化完了: sessionId={} userSid={} amount={}", session.getId(), userSid, creditAmount);
                            } else {
                                if (planIdStr == null) {
                                    log.warn("[StripeWebhook] メタデータに planId がありません（通常のプラン購入として処理不可）。metadata={}", session.getMetadata());
                                    break;
                                }
                                int planSid;
                                try {
                                    planSid = Integer.parseInt(planIdStr);
                                } catch (NumberFormatException nfe) {
                                    log.warn("[StripeWebhook] planId が数値ではありません。値='{}'. このイベントはスキップします。", planIdStr);
                                    break;
                                }
                                log.debug("[StripeWebhook] プラン購入永続化へ委譲: userSid={} planSid={} sessionId={}", userSid, planSid, session.getId());
                                stripeWebhookService.persistCheckoutCompleted(userSid, planSid, session);
                                log.info("[StripeWebhook] プラン購入永続化完了: sessionId={} userSid={} planSid={}", session.getId(), userSid, planSid);
                            }
                        } catch (Exception ex) {
                            log.error("[StripeWebhook] 永続化中にエラー: {}", ex.getMessage(), ex);
                            throw ex; // ensure transaction rollback
                        }
                    } else {
                        log.warn("[StripeWebhook] Session オブジェクトが取得できず、処理をスキップしました。");
                    }
                }
                default -> {
                    // 必要に応じて他イベントもハンドリング
                    log.info("[StripeWebhook] 未対応イベントのためスキップ: {} id={}", type, eventId);
                }
            }
            log.info("[StripeWebhook] ハンドラ処理完了: eventId={}", eventId);
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException e) {
            log.warn("[StripeWebhook] 署名検証に失敗しました: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid-signature");
        } catch (Exception e) {
            log.error("[StripeWebhook] ハンドラ処理中にエラー: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");
        }
    }
}
