package com.aitaskmanager.service.subscription;

import com.aitaskmanager.repository.customMapper.SubscriptionsCustomMapper;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * サブスクリプションのリコンシリエーションを行うサービス
 */
@Service
public class SubscriptionReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionReconciliationService.class);

    @Autowired
    private SubscriptionsCustomMapper subscriptionsCustomMapper;

    @Value("${stripe.apiKey:}")
    private String stripeApiKey;

    /**
     * 日次のリコンシリエーション（毎日 02:30）
     */
    @Scheduled(cron = "0 30 2 * * *")
    public void reconcileActiveSubscriptions() {
        if (stripeApiKey == null || stripeApiKey.isBlank()) {
            log.warn("Stripe API key not configured; skip reconciliation");
            return;
        }
        Stripe.apiKey = stripeApiKey;
        List<Map<String, Object>> rows = subscriptionsCustomMapper.selectActiveWithStripeId();
        log.info("Reconciling {} active subscriptions with Stripe IDs", rows.size());
        for (Map<String, Object> row : rows) {
            String subId = (String) row.get("stripe_subscription_id");
            try {
                Subscription sub = Subscription.retrieve(subId);
                Boolean cancelAtPeriodEnd = sub.getCancelAtPeriodEnd();
                Long currentPeriodEnd = sub.getCurrentPeriodEnd();
                String status = sub.getStatus();
                if (Boolean.TRUE.equals(cancelAtPeriodEnd) && currentPeriodEnd != null) {
                    Timestamp expiresAt = new Timestamp(currentPeriodEnd * 1000L);
                    subscriptionsCustomMapper.updateExpiresAtByStripeId(subId, expiresAt);
                    log.info("Updated expires_at for {} to {}", subId, expiresAt);
                }
                if ("canceled".equalsIgnoreCase(status)) {
                    Long canceledAt = sub.getCanceledAt();
                    Timestamp canceledTs = (canceledAt != null) ? new Timestamp(canceledAt * 1000L) : (currentPeriodEnd != null ? new Timestamp(currentPeriodEnd * 1000L) : null);
                    subscriptionsCustomMapper.cancelByStripeId(subId, canceledTs);
                    log.info("Marked subscription {} as CANCELLED at {}", subId, canceledTs);
                }
            } catch (StripeException se) {
                log.error("Stripe error retrieving subscription {}: {}", subId, se.getMessage());
            } catch (Exception ex) {
                log.error("Error reconciling subscription {}: {}", subId, ex.getMessage(), ex);
            }
        }
    }
}