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
          左メニュー「商品カタログ（Products）」から各プランに対応する Product と Price（月額など）を作成します。
        </li>
      </ol>

      <h5 style={{ marginTop: 16 }}>3. Webhook の設定（ローカル開発）</h5>
      <ol>
        <li>
          バックエンドの受け口は <code>/webhook/stripe</code> を使用します（署名検証あり）。
        </li>
        <li style={{ marginTop: 8 }}>
          Stripe CLI を使ってローカルへ転送します：
          <pre style={{ whiteSpace: "pre-wrap" }}>{`stripe login\nstripe listen --forward-to localhost:8080/webhook/stripe`}</pre>
        </li>
        <li style={{ marginTop: 8 }}>
          コンソールに表示される <code>Signing secret: whsec_...</code> を <code>application.properties</code> の <code>stripe.webhookSecret</code> に設定します。
        </li>
      </ol>

      <h5 style={{ marginTop: 16 }}>4. バックエンド設定</h5>
      <ul>
        <li>
          <code>back/src/main/resources/application.properties</code> に以下を設定（テストキーでOK）：
          <pre style={{ whiteSpace: "pre-wrap" }}>{`stripe.apiKey=sk_test_...\nstripe.webhookSecret=whsec_...\nstripe.successUrl=http://localhost:5173/stripe/success\nstripe.cancelUrl=http://localhost:5173/stripe/cancel`}</pre>
        </li>
        <li>Checkoutは <b>一回払い（PAYMENTモード）</b> を使用しています。RecurringのPriceを使う場合はSUBSCRIPTIONへ切り替えます。</li>
      </ul>

      <h5 style={{ marginTop: 16 }}>5. フロントの動作フロー（概要）</h5>
      <ol>
        <li>プラン変更モーダルから「購入」を押下 → サーバの <code>/api/billing/checkout-session</code> を呼ぶ。</li>
        <li>
          返ってきた <code>sessionUrl</code> にリダイレクトして Stripe Checkout で支払い。
        </li>
        <li>成功/キャンセル後に <code>/stripe/success</code> / <code>/stripe/cancel</code> へ戻ります（ページは用意済み、成功は自動でダッシュボードへ遷移）。</li>
        <li>必要に応じて Webhook（<code>checkout.session.completed</code>）で決済履歴などをDBへ保存します。</li>
      </ol>

      <h5 style={{ marginTop: 16 }}>6. テスト環境時の注意点</h5>
      <ul>
        <li>ローカル開発でStripeのWebhookを受ける間は、stripe listenを起動しっぱなしにする必要があります。</li>
        <li>StripeはクラウドからあなたのPCに直接届きません。stripe listenがトンネル役になって、Stripe→(CLI)→http://localhost:8080/webhook/stripe に転送します。CLIを止めると転送も止まります。</li>
        <li>本番など、インターネットから到達可能な公開URLをStripeダッシュボードに登録している場合は不要です。</li>
      </ul>

      <h5 style={{ marginTop: 16 }}>7. 本番切り替えの注意点</h5>
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
