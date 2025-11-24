import { useState } from "react";
import axios, { AxiosError } from "axios";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/authContext";
import "./Login.css";

export default function LoginPage() {
  const { setAuth } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      const response = await axios.post("http://localhost:8080/api/auth/login", {
        username,
        password,
      });
      const { accessToken, refreshToken } = response.data;
      setAuth({ accessToken, refreshToken, username });
      navigate("/dashboard");
    } catch (error: unknown) {
      let message = "ユーザー名またはパスワードが違います";
      if (axios.isAxiosError(error)) {
        const axiosError = error as AxiosError<{ message: string }>;
        if (axiosError.response?.data?.message) {
          message = axiosError.response.data.message;
        }
      }
      setError(message);
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
              <form onSubmit={handleLogin}>
                <div className="mb-3">
                  <label className="form-label">ユーザー名</label>
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
                {error && <div className="alert alert-danger text-center py-2">{error}</div>}
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
              </form>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}