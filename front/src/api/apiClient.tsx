import axios from "axios";

const apiClient = axios.create({
  baseURL: "http://localhost:8080",
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