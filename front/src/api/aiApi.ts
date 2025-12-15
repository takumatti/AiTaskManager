import apiClient from './apiClient';
// AIクォータステータスの型定義
export type AiQuotaStatus = {
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
