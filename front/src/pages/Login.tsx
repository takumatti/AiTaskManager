import { useEffect, useState } from "react";
import axios, { AxiosError } from "axios";
import apiClient from "../api/apiClient";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/authContext";
import "./Login.css";

// LoginPage コンポーネント
export default function LoginPage() {
  const { setAuth } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  // Login form UX: 送信試行フラグ（フォーカスアウト検証は行わない）
  const [loginSubmitAttempted, setLoginSubmitAttempted] = useState(false);
  // Register form state
  const [showRegister, setShowRegister] = useState(false);
  const [regUsername, setRegUsername] = useState("");
  const [regEmail, setRegEmail] = useState("");
  const [regPassword, setRegPassword] = useState("");
  // Register form UX: 入力済み/送信試行フラグ
  const [regSubmitAttempted, setRegSubmitAttempted] = useState(false);
  const [regPasswordTouched, setRegPasswordTouched] = useState(false);
  // 登録エラーの発生源（submit or server）
  const [regErrorSource, setRegErrorSource] = useState<'submit' | 'server' | null>(null);

  // 登録フォーム表示切替時に状態をクリーンリセット
  useEffect(() => {
    if (showRegister) {
      setRegisterError("");
      setRegSubmitAttempted(false);
      setRegPasswordTouched(false);
      setRegErrorSource(null);
      // ログイン側の状態も安全のためクリア
      setLoginError("");
      setLoginSubmitAttempted(false);
    } else {
      // 登録フォームを閉じたら関連状態のみクリア
      setRegErrorSource(null);
    }
  }, [showRegister]);
  // エラー表示（ログイン/登録で独立管理）
  const [loginError, setLoginError] = useState("");
  const [registerError, setRegisterError] = useState("");
  const [loading, setLoading] = useState(false);

  // ログイン処理
  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setLoginError("");
    setLoginSubmitAttempted(true);
    try {
      // 必須チェック（フロント）
      if (!username.trim()) {
        setLoginError("ユーザー名 / メールアドレスは必須です");
        setLoading(false);
        return;
      }
      if (!password.trim()) {
        setLoginError("パスワードは必須です");
        setLoading(false);
        return;
      }
      const response = await apiClient.post("/api/auth/login", {
        username,
        password,
      });
      const { accessToken, refreshToken, userId } = response.data as { accessToken: string; refreshToken: string; userId?: number };
      setAuth({ accessToken, refreshToken, username, userId });
      navigate("/dashboard");
    } catch (error: unknown) {
  let message = "ユーザー名またはパスワードが違います";
      if (axios.isAxiosError(error)) {
        const axiosError = error as AxiosError<{ message: string }>;
        if (axiosError.response?.data?.message) {
          message = axiosError.response.data.message;
        }
      }
  setLoginError(message);
    } finally {
      setLoading(false);
    }
  };

  // 登録処理
  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setRegisterError("");
    setRegSubmitAttempted(true);
    setRegErrorSource('submit');
    try {
      
      if (!regUsername.trim()) {
        setRegisterError("ユーザー名は必須です");
        setLoading(false);
        return;
      }
      if (!regEmail.trim()) {
        setRegisterError("メールアドレスは必須です");
        setLoading(false);
        return;
      }
      
      if (regPassword.length < 8) {
        setRegisterError("パスワードは8文字以上にしてください");
        setLoading(false);
        return;
      }
      if (regUsername.toLowerCase() === regPassword.toLowerCase()) {
        setRegisterError("パスワードをユーザー名と同一にできません");
        setLoading(false);
        return;
      }
      const response = await apiClient.post("/api/auth/register", {
        username: regUsername,
        email: regEmail,
        password: regPassword,
      });
      const { accessToken, refreshToken, userId } = response.data as { accessToken: string; refreshToken: string; userId?: number };
      setAuth({ accessToken, refreshToken, username: regUsername, userId });
      navigate("/dashboard");
    } catch (error: unknown) {
  let message = "登録に失敗しました";
      if (axios.isAxiosError(error)) {
        const axiosError = error as AxiosError<{ message: string }>;
        if (axiosError.response?.data?.message) {
          message = axiosError.response.data.message;
        }
      }
  setRegisterError(message);
      setRegErrorSource('server');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-bg min-vh-100 d-flex align-items-center justify-content-center">
      <div className="container">
        <div className="row justify-content-center">
          <div className="col-12 col-sm-10 col-md-8 col-lg-5 col-xl-4">
            <div className="card login-card shadow-lg p-4 border-0">
              <div className="text-center mb-4">
                <h2 className="fw-bold mb-2">ログイン</h2>
                <p className="text-muted mb-0">AIタスク管理へようこそ</p>
              </div>
              {showRegister ? (
                <form onSubmit={(e) => e.preventDefault()} noValidate>
                  <div className="mb-3">
                    <label className="form-label">ユーザー名</label>
                    <input
                      type="text"
                      className="form-control form-control-lg"
                      value={regUsername}
                      onChange={(e) => setRegUsername(e.target.value)}
                      required
                    />
                  </div>
                  <div className="mb-3">
                    <label className="form-label">メールアドレス</label>
                    <input
                      type="email"
                      className="form-control form-control-lg"
                      value={regEmail}
                      onChange={(e) => setRegEmail(e.target.value)}
                      required
                    />
                  </div>
                  <div className="mb-3">
                    <label className="form-label">パスワード (8文字以上)</label>
                    <input
                      type="password"
                      className="form-control form-control-lg"
                      value={regPassword}
                      onChange={(e) => setRegPassword(e.target.value)}
                      onBlur={() => setRegPasswordTouched(true)}
                      required
                    />
                    {regPasswordTouched && regPassword.length > 0 && regPassword.length < 8 && (
                      <small className="text-danger">8文字以上のパスワードを入力してください</small>
                    )}
                  </div>
                  {showRegister && regSubmitAttempted && regErrorSource === 'submit' && registerError && (
                    <div className="alert alert-danger text-center py-2">{registerError}</div>
                  )}
                  <div className="d-grid mt-4">
                    <button
                      type="button"
                      className="btn btn-success btn-lg"
                      disabled={loading}
                      onClick={handleRegister}
                    >
                      {loading ? <span className="spinner-border spinner-border-sm me-2" role="status" /> : null}
                      {loading ? "登録中..." : "登録"}
                    </button>
                  </div>
                  <div className="text-center mt-3">
                    <button type="button" className="btn btn-link" onClick={() => { setRegisterError(""); setRegUsername(""); setRegEmail(""); setRegPassword(""); setRegSubmitAttempted(false); setRegPasswordTouched(false); setShowRegister(false); }}>
                      既にアカウントをお持ちの方はこちら
                    </button>
                  </div>
                </form>
              ) : (
                <form onSubmit={handleLogin} noValidate>
                  <div className="mb-3">
                    <label className="form-label">ユーザー名 / メールアドレス</label>
                    <input
                      type="text"
                      className="form-control form-control-lg"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      required
                      autoFocus
                    />
                  </div>
                  <div className="mb-3">
                    <label className="form-label">パスワード</label>
                    <input
                      type="password"
                      className="form-control form-control-lg"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                    />
                  </div>
                  {loginSubmitAttempted && loginError && <div className="alert alert-danger text-center py-2">{loginError}</div>}
                  <div className="d-grid mt-4">
                    <button
                      type="submit"
                      className="btn btn-primary btn-lg login-btn"
                      disabled={loading}
                    >
                      {loading ? <span className="spinner-border spinner-border-sm me-2" role="status" /> : null}
                      {loading ? "ログイン中..." : "ログイン"}
                    </button>
                  </div>
                  <div className="text-center mt-3">
                    <button type="button" className="btn btn-link" onClick={() => { setLoginError(""); setRegUsername(""); setRegEmail(""); setRegPassword(""); setRegSubmitAttempted(false); setRegPasswordTouched(false); setLoginSubmitAttempted(false); setShowRegister(true); }}>
                      新規登録はこちら
                    </button>
                  </div>
                </form>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}