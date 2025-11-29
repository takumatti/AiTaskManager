import { useState, useEffect } from "react";
import type { ReactNode } from "react";
import type { AuthState } from "../types/auth";
import { saveTokens, clearTokens } from "../utils/authUtils";
import { AuthContext } from "../context/authContext";
import apiClient from "../api/apiClient";
import { AxiosError } from "axios";
  // 型ガード: AxiosError かどうか
  function isAxiosError(error: unknown): error is AxiosError<{ message: string }> {
    return (
      typeof error === "object" &&
      error !== null &&
      "isAxiosError" in error &&
      Boolean((error as Record<string, unknown>).isAxiosError)
    );
  }

interface Props { children: ReactNode }

export const AuthProvider = ({ children }: Props) => {
  const [auth, setAuthState] = useState<AuthState>({ accessToken: null, refreshToken: null, username: undefined, userId: undefined });
  const [error, setError] = useState("");

  useEffect(() => {
    const accessToken = localStorage.getItem("accessToken");
    const refreshToken = localStorage.getItem("refreshToken");
    const username = localStorage.getItem("username") || undefined;
    const userIdStr = localStorage.getItem("userId");
    const userId = userIdStr ? Number(userIdStr) : undefined;

    if (accessToken && refreshToken) {
      setTimeout(() => setAuthState({ accessToken, refreshToken, username, userId }), 0);
    }
  }, []);

  const setAuth = (newAuth: AuthState) => {
    setAuthState(newAuth);
    if (newAuth.accessToken && newAuth.refreshToken) saveTokens(newAuth.accessToken, newAuth.refreshToken);
    else clearTokens();
    if (newAuth.username) localStorage.setItem("username", newAuth.username);
    else localStorage.removeItem("username");
    if (typeof newAuth.userId === "number") localStorage.setItem("userId", String(newAuth.userId));
    else localStorage.removeItem("userId");
  };

  // リフレッシュトークンAPI呼び出し
  const refreshToken = async () => {
    setError("");
    try {
      const refreshToken = localStorage.getItem("refreshToken");
      if (!refreshToken) throw new Error("リフレッシュトークンがありません");
      const res = await apiClient.post("/api/auth/refresh", { refreshToken });
      const { accessToken, refreshToken: newRefreshToken, userId } = res.data;
      setAuth({ ...auth, accessToken, refreshToken: newRefreshToken, userId });
    } catch (err: unknown) {
      if (isAxiosError(err) && err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError("トークンの更新に失敗しました");
        console.error(err);
      }
    }
  };

  // ログアウトAPI呼び出し
  const logout = async () => {
    setError("");
    try {
      const refreshToken = localStorage.getItem("refreshToken");
      if (refreshToken) {
        await apiClient.post("/api/auth/logout", { refreshToken });
      }
    } catch (err: unknown) {
      if (isAxiosError(err) && err.response?.data?.message) {
        // 画面に不要ならログだけ
        console.error("ログアウトAPIエラー:", err.response.data.message);
      } else {
        console.error("ログアウトAPIエラー:", err);
      }
    } finally {
      setAuth({ accessToken: null, refreshToken: null });
      clearTokens();
      localStorage.removeItem("username");
      try {
        if (apiClient && apiClient.defaults && apiClient.defaults.headers) {
          if (apiClient.defaults.headers.common) {
            delete apiClient.defaults.headers.common["Authorization"];
          }
        }
      } catch {
        // noop
      }
    }
  };

  return (
    <AuthContext.Provider value={{ auth, setAuth, logout, error, setError, refreshToken }}>
      {children}
    </AuthContext.Provider>
  );
};