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

// JWTのペイロードを安全にデコードする簡易関数
function decodeJwt(token: string | null): Record<string, unknown> | null {
  if (!token) return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const json = JSON.parse(atob(payload));
    return json as Record<string, unknown>;
  } catch {
    return null;
  }
}

export const AuthProvider = ({ children }: Props) => {
  const [auth, setAuthState] = useState<AuthState>({ accessToken: null, refreshToken: null, username: undefined, userId: undefined, roles: undefined });
  const [error, setError] = useState("");

  useEffect(() => {
    const accessToken = localStorage.getItem("accessToken");
    const refreshToken = localStorage.getItem("refreshToken");
    const username = localStorage.getItem("username") || undefined;
  const userIdStr = localStorage.getItem("userId");
  const rolesStr = localStorage.getItem("roles");
  const roles = rolesStr ? JSON.parse(rolesStr) as string[] : undefined;
    const userId = userIdStr ? Number(userIdStr) : undefined;

    if (accessToken && refreshToken) {
      setTimeout(() => setAuthState({ accessToken, refreshToken, username, userId, roles }), 0);
    }
  }, []);

  const setAuth = (newAuth: AuthState) => {
    // accessToken がある場合は即時にロールをデコードして反映
    let roles = newAuth.roles;
    if (newAuth.accessToken) {
      const payload = decodeJwt(newAuth.accessToken);
      roles = Array.isArray(payload?.roles)
        ? (payload!.roles as string[])
        : (typeof payload?.role === "string" ? [payload!.role as string] : roles);
    }
    const nextAuth: AuthState = { ...newAuth, roles };
    setAuthState(nextAuth);
    if (newAuth.accessToken && newAuth.refreshToken) saveTokens(newAuth.accessToken, newAuth.refreshToken);
    else clearTokens();
    if (newAuth.username) localStorage.setItem("username", newAuth.username);
    else localStorage.removeItem("username");
    if (typeof newAuth.userId === "number") localStorage.setItem("userId", String(newAuth.userId));
    else localStorage.removeItem("userId");
    if (roles) localStorage.setItem("roles", JSON.stringify(roles));
    else localStorage.removeItem("roles");
  };

  // リフレッシュトークンAPI呼び出し
  const refreshToken = async () => {
    setError("");
    try {
      const refreshToken = localStorage.getItem("refreshToken");
      if (!refreshToken) throw new Error("リフレッシュトークンがありません");
      const res = await apiClient.post("/api/auth/refresh", { refreshToken });
      const { accessToken, refreshToken: newRefreshToken, userId } = res.data;
      const payload = decodeJwt(accessToken);
      const roles = Array.isArray(payload?.roles)
        ? (payload!.roles as string[])
        : (typeof payload?.role === "string" ? [payload!.role as string] : undefined);
      setAuth({ ...auth, accessToken, refreshToken: newRefreshToken, userId, roles });
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
  setAuth({ accessToken: null, refreshToken: null, roles: undefined });
      clearTokens();
      localStorage.removeItem("username");
  localStorage.removeItem("roles");
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