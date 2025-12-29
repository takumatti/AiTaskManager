import apiClient from './apiClient';

export type CreditPack = {
  creditPackSid: number;
  code: string;
  name: string;
  description: string | null;
  amount: number;
  displayPrice: number;
  stripePriceId: string;
  enabled: boolean;
  sortOrder: number;
  createdAt: string;
};

export async function fetchCreditPacks(): Promise<CreditPack[]> {
  const res = await apiClient.get<CreditPack[]>('/api/credit-packs');
  return res.data;
}
