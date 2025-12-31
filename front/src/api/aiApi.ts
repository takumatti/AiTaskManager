import apiClient from './apiClient';
// AIクォータステータスの型定義
export type AiQuotaStatus = {
  planId: number | null;
  planName: string;
  displayPlanId?: number | null;
  displayPlanName?: string;
  unlimited: boolean;
  remaining: number | null;
  resetDate?: string; // YYYY-MM-DD
  daysUntilReset?: number; // 残日数
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
  aiQuota: number | null; // 4 = 無制限（サーバ側で無制限判定）。フロントはunlimitedフラグを使用。
};

// プラン一覧取得API
export async function fetchPlans(): Promise<SubscriptionPlan[]> {
  const res = await apiClient.get('/api/plans');
  return res.data as SubscriptionPlan[];
}

// プラン変更API
export async function changePlan(planId: number): Promise<void> {
  await apiClient.post('/api/subscriptions/change', { planId });
}

// --- タスク細分化（AI） ---
export type TaskBreakdownRequest = {
  title: string;
  description: string;
  dueDate?: string; // YYYY-MM-DD
  priority?: 'HIGH' | 'NORMAL' | 'LOW';
};

// タスク細分化レスポンスの型定義
export type TaskBreakdownResponse = {
  warning?: string;
  children: Array<{ title: string; description?: string }>;
};

// タスク細分化API
export async function breakdownTask(input: TaskBreakdownRequest): Promise<TaskBreakdownResponse> {
  const res = await apiClient.post('/api/ai/tasks/breakdown', input);
  return res.data as TaskBreakdownResponse;
}

// クレジット決済セッション作成API
export async function createCreditCheckout(priceId: string): Promise<{ sessionUrl: string }> {
  const res = await apiClient.post('/api/billing/checkout-credit', null, {
    params: { priceId },
  });
  // バックエンドのレスポンスDTOが { sessionUrl: string } を返す想定
  return res.data as { sessionUrl: string };
}
