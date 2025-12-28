import { useNavigate } from "react-router-dom";

export default function StripeCancel() {
  const navigate = useNavigate();

  return (
    <div className="container py-5">
      <h2>決済がキャンセルされました</h2>
      <p>ご利用ありがとうございます。プラン選択に戻って、再度お試しください。</p>
      <button className="btn btn-secondary" onClick={() => navigate("/dashboard")}>ダッシュボードへ戻る</button>
    </div>
  );
}
