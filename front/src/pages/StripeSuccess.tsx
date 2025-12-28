import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

export default function StripeSuccess() {
  const navigate = useNavigate();

  useEffect(() => {
    try { localStorage.setItem('purchaseSuccess', 'true'); } catch (e) {
      // localStorage未対応環境などでは無視
      void e;
    }
    const t = setTimeout(() => navigate("/dashboard"), 3000);
    return () => clearTimeout(t);
  }, [navigate]);

  return (
    <div className="container py-5">
      <h2>決済が完了しました</h2>
      <p>ありがとうございます。3秒後にダッシュボードへ戻ります。</p>
      <button className="btn btn-primary" onClick={() => navigate("/dashboard")}>今すぐ戻る</button>
    </div>
  );
}
