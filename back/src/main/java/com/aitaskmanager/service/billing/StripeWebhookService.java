package com.aitaskmanager.service.billing;

import com.aitaskmanager.repository.customMapper.PaymentsCustomMapper;
import com.aitaskmanager.repository.customMapper.SubscriptionsCustomMapper;
import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.customMapper.CustomAiUsageMapper;
import com.aitaskmanager.repository.generator.SubscriptionPlansMapper;
import com.stripe.model.checkout.Session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Stripeのウェブフックイベントに関連するビジネスロジックを提供するサービス
 */
@Service
public class StripeWebhookService {

    @Autowired
    private  PaymentsCustomMapper paymentsMapper;

    @Autowired
    private SubscriptionsCustomMapper subscriptionsMapper;

    @Autowired
    private SubscriptionPlansMapper subscriptionPlansMapper;

    @Autowired
    private CustomAiUsageMapper customAiUsageMapper;
    
    @Autowired
    private UserMapper userMapper;

    /**
     * チェックアウト完了イベントを永続化する
     * 
     * @param userSid ユーザーSID
     * @param planSid プランSID 
     * @param session Stripeセッションオブジェクト
     */
    public void persistCheckoutCompleted(int userSid, int planSid, Session session) {
        String paymentId = session.getPaymentIntent(); 
        // diagnostics
        System.out.println("[StripeWebhookService] persistCheckoutCompleted start userSid=" + userSid + " planSid=" + planSid + " sessionId=" + session.getId());
        System.out.println("[StripeWebhookService] paymentIntent=" + paymentId + ", amount_total=" + session.getAmountTotal() + ", currency=" + session.getCurrency());
        if (paymentId == null || paymentId.isBlank()) {
            paymentId = session.getId();
            System.out.println("[StripeWebhookService] fallback paymentId to sessionId=" + paymentId);
        }
        int exists = paymentsMapper.existsByPaymentId(paymentId);
        System.out.println("[StripeWebhookService] payments existsByPaymentId(" + paymentId + ")=" + exists);
        if (exists == 0) {
            double amount = (session.getAmountTotal() != null ? session.getAmountTotal() / 100.0 : 0.0);
            String currency = session.getCurrency();
            String method = "stripe_checkout";
            String status = session.getPaymentStatus();
            System.out.println("[StripeWebhookService] inserting payment userSid=" + userSid + ", amount=" + amount + ", currency=" + currency + ", method=" + method + ", status=" + status + ", paymentId=" + paymentId);
            paymentsMapper.insertPayment(userSid, amount, currency, method, paymentId, status);
        }

        var plan = subscriptionPlansMapper.selectByPrimaryKey(planSid);
        boolean isUnlimited = plan != null && plan.getAiQuota() == null;
        Timestamp expiresAt = null;
        if (isUnlimited) {
            long now = System.currentTimeMillis();
            // 30日後に設定
            expiresAt = new Timestamp(now + 30L * 24 * 60 * 60 * 1000);
        }
        System.out.println("[StripeWebhookService] inserting subscription userSid=" + userSid + ", planSid=" + planSid + ", isUnlimited=" + isUnlimited + ", expiresAt=" + expiresAt);
        subscriptionsMapper.insertSubscription(userSid, planSid, expiresAt);
        
        // ユーザテーブルの plan_id を更新（SIDベースで確実に更新）
        try {
            userMapper.updatePlanIdBySid(userSid, planSid);
        } catch (Exception e) {
            System.err.println("[StripeWebhookService] updatePlanIdBySid failed: " + e.getMessage());
        }
        
        // 当月のai_usage行を初期化（存在しない場合は作成）。ボーナス0でUPSERTを使って行を用意。
        LocalDate now = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        try {
            customAiUsageMapper.upsertAddBonus(userSid, now.getYear(), now.getMonthValue(), 0);
            System.out.println("[StripeWebhookService] ai_usage ensured for userSid=" + userSid + " year=" + now.getYear() + " month=" + now.getMonthValue());
        } catch (Exception e) {
            // ai_usageの初期化失敗は致命ではないためログのみ
            System.err.println("[StripeWebhookService] ai_usage ensure failed: " + e.getMessage());
        }
    }

    /**
     * クレジットパック購入の永続化（支払い記録＋当月ボーナス加算）
     *
     * @param userSid ユーザーSID
     * @param creditAmount 追加する回数
     * @param session Stripeセッションオブジェクト
     */
    public void persistCreditPack(int userSid, int creditAmount, Session session) {
        String paymentId = session.getPaymentIntent();
        System.out.println("[StripeWebhookService] persistCreditPack start userSid=" + userSid + " creditAmount=" + creditAmount + " sessionId=" + session.getId());
        if (paymentId == null || paymentId.isBlank()) {
            paymentId = session.getId();
            System.out.println("[StripeWebhookService] fallback paymentId to sessionId=" + paymentId);
        }
        int exists = paymentsMapper.existsByPaymentId(paymentId);
        System.out.println("[StripeWebhookService] payments existsByPaymentId(" + paymentId + ")=" + exists);
        if (exists == 0) {
            double amount = (session.getAmountTotal() != null ? session.getAmountTotal() / 100.0 : 0.0);
            String currency = session.getCurrency();
            String method = "stripe_checkout";
            String status = session.getPaymentStatus();
            System.out.println("[StripeWebhookService] inserting payment(userSid=" + userSid + ", amount=" + amount + ", currency=" + currency + ", method=" + method + ", status=" + status + ", paymentId=" + paymentId + ")");
            paymentsMapper.insertPayment(userSid, amount, currency, method, paymentId, status);
        }

        // 当月にボーナス回数を加算
        java.time.LocalDate now = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo"));
        customAiUsageMapper.upsertAddBonus(userSid, now.getYear(), now.getMonthValue(), creditAmount);
        System.out.println("[StripeWebhookService] bonus_count added: +" + creditAmount + " for userSid=" + userSid + " year=" + now.getYear() + " month=" + now.getMonthValue());
    }
}