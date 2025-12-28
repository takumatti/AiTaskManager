import apiClient from './apiClient';
// AIクォータステータスの型定義
export type AiQuotaStatus = {
  planId: number | null;
  planName: string;
  unlimited: boolean;
  remaining: number | null;
};
// AIクォータステータス取得API
export async function fetchAiQuotaStatus(): Promise<AiQuotaStatus> {
  const res = await apiClient.get('/api/ai/quota');
  return res.data as AiQuotaStatus;
}

// サブスクリプションプランの型
export type SubscriptionPlan = {
  id: number;
  name: string;
  description?: string; // 追加説明
  aiQuota: number | null; // null = 無制限
};

// プラン一覧取得（暫定：エンドポイントは /api/plans を想定）
export async function fetchPlans(): Promise<SubscriptionPlan[]> {
  const res = await apiClient.get('/api/plans');
  return res.data as SubscriptionPlan[];
}

// プラン変更（暫定：エンドポイントは /api/subscriptions/change を想定）
export async function changePlan(planId: number): Promise<void> {
  await apiClient.post('/api/subscriptions/change', { planId });
}

// --- クレジットパック（回数追加） ---
export const CREDIT_PRICE_5 = 'price_credit_5';
export const CREDIT_PRICE_10 = 'price_credit_10';
export const CREDIT_PRICE_30 = 'price_credit_30';

export async function createCreditCheckout(priceId: string): Promise<{ sessionUrl: string }> {
  const res = await apiClient.post('/api/billing/checkout-credit', null, {
    params: { priceId },
  });
  // バックエンドのレスポンスDTOが { sessionUrl: string } を返す想定
  return res.data as { sessionUrl: string };
}
