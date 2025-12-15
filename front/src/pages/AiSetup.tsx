import React from "react";

const AiSetup: React.FC = () => {
  return (
    <div className="container" style={{ maxWidth: 800, padding: "16px" }}>
      <h2>AI連携の設定手順</h2>
      <p className="text-muted" style={{ marginBottom: 16 }}>
        OpenAI API キーをアプリケーションに設定し、AI分解機能を有効化するための手順です。
      </p>

      <ol>
        <li>
          OpenAI のアカウントで API キーを取得します。
          <div><a href="https://platform.openai.com/" target="_blank" rel="noreferrer">OpenAI Platform</a></div>
        </li>
        <li style={{ marginTop: 8 }}>
          サーバー側に環境変数 <code>OPENAI_API_KEY</code> を設定するか、
          <code>application.properties</code> に <code>openai.apiKey=&lt;取得したキー&gt;</code> を記載します。
        </li>
        <li style={{ marginTop: 8 }}>
          必要に応じてタイムアウト・リトライ・サーキットブレーカーの設定を <code>application.properties</code> に追加します。
          <pre style={{ background: "#f7f7f7", padding: 12, borderRadius: 6 }}>
openai.apiKey=YOUR_OPENAI_API_KEY
openai.timeoutSeconds=15
openai.retry.maxAttempts=2
openai.retry.initialBackoffMillis=300
openai.cb.failureThreshold=3
openai.cb.cooldownSeconds=60
          </pre>
        </li>
        <li style={{ marginTop: 8 }}>
          アプリケーションを再起動し、ダッシュボード右上の「AI利用状況」が取得できることを確認してください。
        </li>
      </ol>

      <div style={{ marginTop: 24 }}>
        <p>
          もし 503 (Service Unavailable) が表示される場合は、APIキーが未設定の可能性があります。設定完了後に「再試行」ボタンを押して更新してください。
        </p>
      </div>
    </div>
  );
};

export default AiSetup;