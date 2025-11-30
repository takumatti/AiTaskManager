import axios from "axios";

// 環境変数からAPIベースURLを取得（Vite: import.meta.env）
// Viteの型では import.meta.env は Record<string, string | boolean | undefined>
const { VITE_API_BASE_URL } = import.meta.env as unknown as { VITE_API_BASE_URL?: string };
const BASE_URL = VITE_API_BASE_URL && VITE_API_BASE_URL !== ""
  ? VITE_API_BASE_URL
  : "http://localhost:8080";

const apiClient = axios.create({
  baseURL: BASE_URL,
});

// リクエスト時に JWT を付与
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem("accessToken");
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// レスポンス時に 401 を検知して自動ログアウト
apiClient.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem("accessToken");
      localStorage.removeItem("refreshToken");
      window.location.href = "/login"; // セッション切れ → ログインへ強制送還
    }
    return Promise.reject(err);
  }
);

export default apiClient;