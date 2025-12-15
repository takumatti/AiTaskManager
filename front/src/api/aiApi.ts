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
