import React from "react";

const StripeSetup: React.FC = () => {
  return (
    <div className="container" style={{ maxWidth: 900, padding: "16px" }}>
      <h2>Stripe 導入手順</h2>
      <p className="text-muted" style={{ marginBottom: 16 }}>
        プラン変更時の決済に Stripe を利用するための設定手順です。まずはテスト環境で動作確認し、本番切替は最後に行ってください。
      </p>

      <h5>1. Stripe アカウントの準備</h5>
      <ol>
        <li>
          Stripe にサインアップし、ダッシュボードにアクセスします。
          <div><a href="https://dashboard.stripe.com/" target="_blank" rel="noreferrer">Stripe Dashboard</a></div>
        </li>
        <li style={{ marginTop: 8 }}>
          テストモード（sk_test...）の API キーを取得します（開発中は必ずテストキー）。
        </li>
      </ol>

      <h5 style={{ marginTop: 16 }}>2. Products / Prices の作成</h5>
      <ol>
        <li>
          左メニュー「Products」から各プランに対応する Product と Price（月額など）を作成します。
        </li>
        <li style={{ marginTop: 8 }}>
          後でバックエンドの `subscription_plans` に <code>stripe_price_id</code> を紐付ける設計にします（あとで実装）。
        </li>
      </ol>

      <h5 style={{ marginTop: 16 }}>3. Webhook の設定</h5>
      <ol>
        <li>
          ダッシュボード「Developers → Webhooks」で受信エンドポイントを登録します（後でサーバー側に <code>/api/billing/webhook</code> を用意）。
        </li>
        <li style={{ marginTop: 8 }}>
          署名検証用の <code>webhook signing secret</code> を控えておきます（<code>stripe.webhookSecret</code>）。
        </li>
        <li style={{ marginTop: 8 }}>
          ローカル開発では Stripe CLI を使い、Webhook をローカルへフォワードするのが便利です（任意）。
        </li>
      </ol>

      <h5 style={{ marginTop: 16 }}>4. バックエンド設定</h5>
      <ul>
        <li>
          `back/src/main/resources/application.properties` に以下を設定（テストキーでOK）：
          <pre style={{ whiteSpace: "pre-wrap" }}>{`stripe.apiKey=sk_test_...\nstripe.webhookSecret=whsec_...\napp.baseUrl=http://localhost:5173`}</pre>
        </li>
        <li>
          後で次の API を追加します：
          <ul>
            <li>POST <code>/api/billing/checkout</code>（Checkout セッション作成）</li>
            <li>POST <code>/api/billing/webhook</code>（決済完了の受信／DB反映）</li>
            <li>GET <code>/api/billing/history</code>（決済履歴の取得）</li>
            <li>GET <code>/api/billing/subscription</code>（契約情報の取得）</li>
          </ul>
        </li>
      </ul>

      <h5 style={{ marginTop: 16 }}>5. フロントの動作フロー（概要）</h5>
      <ol>
        <li>
          プラン変更モーダルから「購入/変更」を押下 → サーバの <code>/api/billing/checkout</code> を呼ぶ。
        </li>
        <li>
          返ってきた <code>sessionUrl</code> にリダイレクトして Stripe Checkout で支払い。
        </li>
        <li>
          Webhook で <code>checkout.session.completed</code> 等を受信 → DBへ「決済履歴」「契約情報」「ユーザーの planId」などを反映。
        </li>
        <li>
          ダッシュボードへ戻ったら <code>/api/billing/subscription</code> を再取得して表示更新（AI枠もプランに応じて更新）。
        </li>
      </ol>

      <h5 style={{ marginTop: 16 }}>6. 本番切り替えの注意点</h5>
      <ul>
        <li>本番キー（sk_live...）に切り替えたら、Price も本番のものを参照してください。</li>
        <li>Webhook の URL と署名シークレットも本番用に再設定が必要です。</li>
        <li>UIには必ず「テスト/本番」表示を入れると誤課金防止になります。</li>
      </ul>

      <div style={{ marginTop: 24 }}>
        <p className="text-muted">
          まずはテストモードで E2E（Checkout → Webhook → DB反映 → UI更新）を通し、その後に本番へ切り替えてください。
        </p>
      </div>
    </div>
  );
};

export default StripeSetup;
