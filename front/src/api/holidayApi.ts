import axios from './apiClient';

// 祝日データの型定義
export interface PublicHoliday {
  date: string;       // YYYY-MM-DD
  localName: string;  // 日本語名
  name: string;       // 英語名
  countryCode: string;
  fixed: boolean;
  global: boolean;
  launchYear?: number;
  type: string;       // Public など
}

// 祝日取得API
export const fetchHolidays = async (year: number): Promise<PublicHoliday[]> => {
  try {
    const res = await axios.get('/api/holidays', { params: { year } });
    return res.data;
  } catch (e: unknown) {
    let msg = '祝日取得失敗';
    if (typeof e === 'object' && e !== null) {
      // axios error shape 部分的アクセス（型厳密化は省略）
      const errObj = e as { response?: { status?: number; data?: { message?: string } }; message?: string };
      const status = errObj.response?.status;
      const backendMsg = errObj.response?.data?.message;
      if (status === 401) {
        msg = backendMsg || '認証が必要です (祝日API)';
      } else if (status === 502 || status === 503) {
        msg = backendMsg || '外部祝日サービスへの接続に失敗しました';
      } else if (backendMsg) {
        msg = backendMsg;
      } else if (errObj.message) {
        msg = errObj.message;
      }
    } else if (typeof e === 'string') {
      msg = e;
    }
    console.warn('fetchHolidays error:', msg);
    throw new Error(msg);
  }
};
