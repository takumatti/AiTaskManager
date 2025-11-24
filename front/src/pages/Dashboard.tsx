import { useAuth } from "../context/authContext";
import { useNavigate } from "react-router-dom";

export default function Dashboard() {
  const { logout, auth } = useAuth();
  const navigate = useNavigate();

  return (
    <div style={{ padding: "2rem" }}>
      <h1>ダッシュボード</h1>
      <p>ようこそ {auth.username} さん！</p>
      <button
        onClick={() => {
          logout();
          navigate('/login');
        }}
        style={{
          marginTop: "20px",
          padding: "10px 20px",
          background: "#e11d48",
          color: "#fff",
          border: "none",
          borderRadius: "8px",
          cursor: "pointer",
        }}
      >
        ログアウト
      </button>
    </div>
  );
}