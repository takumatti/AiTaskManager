package com.aitaskmanager.service.billing;

import com.stripe.Stripe;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.aitaskmanager.repository.generator.SubscriptionPlansMapper;
import com.aitaskmanager.repository.customMapper.CreditPacksCustomMapper;
import com.aitaskmanager.repository.model.CreditPacks;
import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.model.SubscriptionPlans;
import com.aitaskmanager.repository.model.Users;

import java.util.Optional;

/**
 * Stripe請求サービス
 */
@Service
public class StripeBillingService {
    private static final Logger log = LoggerFactory.getLogger(StripeBillingService.class);

    @Value("${stripe.apiKey}")
    private String stripeApiKey;

    @Autowired
    private SubscriptionPlansMapper subscriptionPlansMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CreditPacksCustomMapper creditPacksCustomMapper;

    @Value("${stripe.successUrl}")
    private String successUrl;

    @Value("${stripe.cancelUrl}")
    private String cancelUrl;

    /**
     * 指定のプランIDに対応するStripe Price IDを取得
     * 
     * @param planId プランID
     * @return Stripe Price IDのOptional
     */
    private Optional<String> findStripePriceIdByPlanId(int planId) {
        SubscriptionPlans plan = subscriptionPlansMapper.selectByPrimaryKey(planId);
        if (plan == null) return Optional.empty();
        String priceId = plan.getStripePriceId();
        if (priceId == null || priceId.isBlank()) return Optional.empty();
        return Optional.of(priceId);
    }

    /**
     * 指定のプランIDに基づいてStripe Checkoutセッションを作成し、セッションURLを返す
     * 
     * @param planId プランID
     * @param userId ユーザーID
     * @return CheckoutセッションのURL
     */
    public String createCheckoutSessionForPlan(int planId, String userId) {
        log.info("[Stripe] createCheckoutSessionForPlan start: planId={} userId={}", planId, userId);
        Stripe.apiKey = stripeApiKey;

        // ダウングレード禁止のバリデーション（無料プランは例外として許可）
        SubscriptionPlans newPlan = subscriptionPlansMapper.selectByPrimaryKey(planId);
        if (newPlan == null) {
            throw new IllegalArgumentException("指定のプランが存在しません: planId=" + planId);
        }
        Integer newQuota = newPlan.getAiQuota();
        boolean isFreeTarget = (newQuota != null && newQuota.intValue() == 0);

        Users user = userMapper.selectByUserId(userId);
        Integer currentPlanId = (user != null) ? user.getPlanId() : null;
        Integer currentQuota = null;
        if (currentPlanId != null) {
            SubscriptionPlans currentPlan = subscriptionPlansMapper.selectByPrimaryKey(currentPlanId);
            currentQuota = (currentPlan != null) ? currentPlan.getAiQuota() : null; // null=無制限
        }

        // 比較のための正規化: null(無制限)はInteger.MAX_VALUE、未設定は-1（比較不能=常にアップ扱い）
        int newComparable = (newQuota == null) ? Integer.MAX_VALUE : newQuota.intValue();
        int currentComparable;
        if (currentPlanId == null) {
            currentComparable = -1; // 現在プランなし => 何に変えてもダウングレードではない
        } else {
            currentComparable = (currentQuota == null) ? Integer.MAX_VALUE : currentQuota.intValue();
        }

        boolean isDowngrade = (currentComparable >= 0) && (newComparable < currentComparable);
        if (isDowngrade && !isFreeTarget) {
            log.warn("[Stripe] downgrade blocked: userId={} currentQuota={} newQuota={}", userId, currentQuota, newQuota);
            throw new IllegalArgumentException("downgrade-not-allowed");
        }

        String priceId = findStripePriceIdByPlanId(planId)
                .orElseThrow(() -> new IllegalArgumentException("対応するStripe Price IDが見つかりません (planId=" + planId + ")"));
        log.info("[Stripe] priceId resolved: {}", priceId);

        log.info("[Stripe] url checks: successUrlBlank={} cancelUrlBlank={} apiKeyPrefix={}",
                (successUrl == null || successUrl.isBlank()),
                (cancelUrl == null || cancelUrl.isBlank()),
                stripeApiKey != null && stripeApiKey.length() >= 7 ? stripeApiKey.substring(0, 7) : null);

        if (successUrl == null || successUrl.isBlank()) {
            throw new IllegalStateException("stripe.successUrl が未設定です");
        }
        if (cancelUrl == null || cancelUrl.isBlank()) {
            throw new IllegalStateException("stripe.cancelUrl が未設定です");
        }

        log.info("[Stripe] Creating checkout session: planId={} userId={} priceId={} successUrl={} cancelUrl={} apiKeyPrefix={}",
        planId, userId, priceId, successUrl, cancelUrl,
        stripeApiKey != null && stripeApiKey.length() >= 7 ? stripeApiKey.substring(0, 7) : null);

        SessionCreateParams params = SessionCreateParams.builder()
                // 一回払いのCheckout
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
        .putMetadata("userId", userId)
        .putMetadata("planId", String.valueOf(planId))
        .setSuccessUrl(successUrl)
        .setCancelUrl(cancelUrl)
                .build();

        try {
            log.info("[Stripe] Calling Session.create ...");
            Session session = Session.create(params);
            log.info("[Stripe] Session.create done. sessionId={} url={}", session.getId(), session.getUrl());
            return session.getUrl();
        } catch (InvalidRequestException e) {
            // InvalidRequestの詳細を整形
            var err = e.getStripeError();
            log.error("Stripe InvalidRequest caught. error={} message={} code={} param={} type={}",
                    err, e.getMessage(), err != null ? err.getCode() : null,
                    err != null ? err.getParam() : null,
                    err != null ? err.getType() : null);
            StringBuilder sb = new StringBuilder("Stripe InvalidRequest: ");
            log.error("Building error message string. sb initialized");
            if (err != null) {
                if (err.getType() != null) sb.append("type=").append(err.getType()).append("; ");
                if (err.getCode() != null) sb.append("code=").append(err.getCode()).append("; ");
                if (err.getParam() != null) sb.append("param=").append(err.getParam()).append("; ");
                if (err.getMessage() != null) sb.append("message=").append(err.getMessage());
            } else {
                sb.append(e.getMessage());
            }
            log.error("InvalidRequest error message assembled: {}", sb);
            throw new RuntimeException("Stripe Checkout セッション作成に失敗しました: " + sb.toString(), e);
        } catch (StripeException e) {
            // InvalidRequest以外でもStripeErrorが付与される場合があるため詳細を整形
            var err = e.getStripeError();
            StringBuilder sb = new StringBuilder();
            if (err != null) {
                sb.append("type=").append(err.getType()).append("; ");
                if (err.getCode() != null) sb.append("code=").append(err.getCode()).append("; ");
                if (err.getParam() != null) sb.append("param=").append(err.getParam()).append("; ");
                if (err.getMessage() != null) sb.append("message=").append(err.getMessage());
            } else if (e.getMessage() != null) {
                sb.append(e.getMessage());
            } else {
                sb.append("unknown");
            }
            log.error("StripeException error message assembled: {}", sb);
            throw new RuntimeException("Stripe Checkout セッション作成に失敗しました: " + sb.toString(), e);
        }
    }

    /**
     * クレジットパック用のCheckoutセッションを作成（Price IDからクレジット数をサーバーで確定）
     * 
     * @param priceId Stripe Price ID
     * @param userId ユーザーID
     * @return CheckoutセッションのURL
     */
    public String createCheckoutSessionForCredit(String priceId, String userId) {
        log.info("[Stripe] createCheckoutSessionForCredit start: priceId={} userId={}", priceId, userId);
        Stripe.apiKey = stripeApiKey;

        // Price ID -> クレジット数の解決（DBのcredit_packsから）
        CreditPacks pack = creditPacksCustomMapper.selectByStripePriceId(priceId);
        if (pack == null || pack.getEnabled() == null || !pack.getEnabled()) {
            throw new IllegalArgumentException("対応する有効なクレジットパックが見つかりません (priceId=" + priceId + ")");
        }
        int creditAmount = pack.getAmount();

        if (successUrl == null || successUrl.isBlank()) {
            throw new IllegalStateException("stripe.successUrl が未設定です");
        }
        if (cancelUrl == null || cancelUrl.isBlank()) {
            throw new IllegalStateException("stripe.cancelUrl が未設定です");
        }

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addLineItem(SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build())
                .putMetadata("userId", userId)
                .putMetadata("type", "credit_pack")
                .putMetadata("amount", String.valueOf(creditAmount))
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .build();

        try {
            log.info("[Stripe] Calling Session.create (credit pack) ...");
            Session session = Session.create(params);
            log.info("[Stripe] Session.create done. sessionId={} url={} (creditAmount={})", session.getId(), session.getUrl(), creditAmount);
            return session.getUrl();
        } catch (InvalidRequestException e) {
            var err = e.getStripeError();
            log.error("Stripe InvalidRequest caught. error={} message={} code={} param={} type={}",
                    err, e.getMessage(), err != null ? err.getCode() : null,
                    err != null ? err.getParam() : null,
                    err != null ? err.getType() : null);
            throw new RuntimeException("Stripe Checkout セッション作成に失敗しました: " + (err != null ? err.getMessage() : e.getMessage()), e);
        } catch (StripeException e) {
            var err = e.getStripeError();
            StringBuilder sb = new StringBuilder();
            if (err != null) {
                sb.append("type=").append(err.getType()).append("; ");
                if (err.getCode() != null) sb.append("code=").append(err.getCode()).append("; ");
                if (err.getParam() != null) sb.append("param=").append(err.getParam()).append("; ");
                if (err.getMessage() != null) sb.append("message=").append(err.getMessage());
            } else if (e.getMessage() != null) {
                sb.append(e.getMessage());
            } else {
                sb.append("unknown");
            }
            log.error("StripeException error message assembled: {}", sb);
            throw new RuntimeException("Stripe Checkout セッション作成に失敗しました: " + sb.toString(), e);
        }
    }

}
