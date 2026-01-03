<div id="top"></div>

# AiTaskManager

<p>
  <img src="https://img.shields.io/badge/-Java-007396.svg?logo=openjdk&style=for-the-badge&logoColor=white">
  <img src="https://img.shields.io/badge/-Spring%20Boot-6DB33F.svg?logo=springboot&style=for-the-badge&logoColor=white">
  <img src="https://img.shields.io/badge/-MyBatis-000000.svg?logo=oracle&style=for-the-badge">
  <img src="https://img.shields.io/badge/-PostgreSQL-336791.svg?logo=postgresql&style=for-the-badge">
  <img src="https://img.shields.io/badge/-React-20232A.svg?logo=react&style=for-the-badge&logoColor=61DAFB">
  <img src="https://img.shields.io/badge/-TypeScript-3178C6.svg?logo=typescript&style=for-the-badge&logoColor=white">
  <img src="https://img.shields.io/badge/-Vite-646CFF.svg?logo=vite&style=for-the-badge&logoColor=white">
  <img src="https://img.shields.io/badge/-Stripe-635BFF.svg?logo=stripe&style=for-the-badge&logoColor=white">
  <img src="https://img.shields.io/badge/-Gradle-02303A.svg?logo=gradle&style=for-the-badge&logoColor=white">
</p>

タスク管理と AI アシスト、Stripe によるプラン/クレジット課金を備えたフルスタックアプリです。バックエンドは Spring Boot + MyBatis + PostgreSQL、フロントエンドは React + TypeScript + Vite を採用しています。

## 目次

1. [プロジェクトについて](#プロジェクトについて)
2. [機能](#機能)
3. [技術スタック](#技術スタック)
4. [ディレクトリ構成](#ディレクトリ構成)
5. [セットアップ](#セットアップ)
6. [実行方法](#実行方法)
7. [Stripe 設定](#stripe-設定)
8. [ドメインルール（重要）](#ドメインルール重要)
9. [トラブルシューティング](#トラブルシューティング)
10. [貢献](#貢献)
11. [ライセンス](#ライセンス)

<div align="right"><a href="#top"><strong>トップへ ↑</strong></a></div>

## プロジェクトについて

AiTaskManager は、個人/チームのタスク管理を中心に「保存時のみ AI 回数を消費」する方針で AI 支援を行い、Stripe によるプラン（サブスクリプション）とクレジット（買い切り）を組み合わせて運用できるアプリケーションです。

## 機能

- タスク管理（リスト/ツリー/カレンダー切替、フィルタ/ソート）
- AI アシスト（保存時のみ回数消費、当月の使用回数/ボーナス管理）
- 課金/請求
  - プラン変更: Stripe Subscription（サブスクリプション）
  - クレジット追加: Stripe Payment（買い切り）
- Webhook による購読ライフサイクル反映（期間末解約/キャンセル）
- 毎日スケジュールの Reconciliation（Stripe とローカル DB の購読状態突合）

<div align="right">(<a href="#top">トップへ</a>)</div>

## 技術スタック

- Backend: Spring Boot, MyBatis (Mapper XML), PostgreSQL
- Frontend: React, TypeScript, Vite
- Billing: Stripe Checkout / Webhooks
- その他: Log4j2, Gradle

> バージョンの詳細は `back/build.gradle` と `front/package.json` を参照してください。

<div align="right">(<a href="#top">トップへ</a>)</div>

## ディレクトリ構成

```
back/         # Spring Boot バックエンド
front/        # React + Vite フロントエンド
db/           # DDL/DML スクリプト（PostgreSQL）
logs/         # アプリケーションログ
```

<div align="right">(<a href="#top">トップへ</a>)</div>

## セットアップ

### 前提

- Windows（PowerShell）想定
- Java 17 以上、Node.js 18 以上
- PostgreSQL 動作環境
- Stripe アカウント（テストモード）

### 環境変数（Backend）

| 変数名 | 役割 | 備考 |
| --- | --- | --- |
| STRIPE_API_KEY | Stripe Secret Key | 例: sk_test_xxx |
| STRIPE_WEBHOOK_SECRET | Webhook 署名検証用 Secret | Stripe CLI の signing secret |
| spring.datasource.url | DB 接続 URL | 例: jdbc:postgresql://localhost:5432/aitaskmanager |
| spring.datasource.username | DB ユーザ | 例: postgres |
| spring.datasource.password | DB パスワード | 例: password |

### 環境変数（Frontend）

| 変数名 | 役割 | 備考 |
| --- | --- | --- |
| VITE_API_BASE_URL | バックエンド API ベース URL | 例: http://localhost:8080 |
| VITE_STRIPE_PUBLIC_KEY | Stripe Publishable Key | 例: pk_test_xxx |

### データベース初期化

- `db/DDL` と `db/DML` を順に適用してください（例: `subscription_plans.sql`, `subscriptions.sql`, `ai_usage.sql` など）。

<div align="right">(<a href="#top">トップへ</a>)</div>

## 実行方法

### バックエンド（Spring Boot）

- VS Code タスクから実行可能
  - Build: 「Gradle: clean build」
  - Run: 「Gradle: bootRun」（バックグラウンド）
- 直接実行する場合（任意）
  - PowerShell: `./gradlew.bat clean build` → `./gradlew.bat bootRun`

### フロントエンド（React + Vite）

- `cd front` → `npm install` → `npm run dev`
- 既定で `http://localhost:5173` が立ち上がります。

<div align="right">(<a href="#top">トップへ</a>)</div>

## Stripe 設定

### Checkout

- プラン変更（subscription）とクレジット購入（payment）をサポート。
- メタデータにユーザ SID 等を付与し、Webhook でローカル DB に反映。

### Webhook

- 署名検証は必須。
- 主要イベント:
  - `checkout.session.completed`: 購入完了（subscription/payment）
  - `customer.subscription.updated`: 期間末解約設定/更新、`expires_at` 反映
  - `customer.subscription.deleted`: 即時キャンセル、CANCELLED 反映
- 冪等性を考慮（同一イベント再送に耐性）。
- ログには event type/id、subscription id、userSid、変更フィールド等を出力。

### Reconciliation（定期監視）

- `@Scheduled` による毎日ジョブで Stripe とローカル DB を突合。
- `subscriptions.stripe_subscription_id` を基に `expires_at`/`status` を補正。

> Webhook が取り逃した場合の安全網として機能します。

<div align="right">(<a href="#top">トップへ</a>)</div>

## ドメインルール（重要）

- AI 使用回数は「保存時のみ消費」。当月の `ai_usage` に `used_count` と `bonus_count` を管理。
- プラン変更は `mode=subscription`、クレジット購入は `mode=payment`。
- Free への変更は Stripe 側で `cancel_at_period_end=true` を設定（期間末解約）。
  - ローカル `subscriptions` は即時 CANCELLED にしない。Webhook/定期監視で終了時に CANCELLED へ。
- `subscriptions.expires_at` は「終了時刻」。継続中は原則 `NULL`。期間末解約設定受信時や終了時に設定。
- Users のプラン更新は SID ベースに統一（`updatePlanIdBySid(userSid, planId)`）。

詳細な設計方針は `.github/copilot-instructions.md` を参照。

<div align="right">(<a href="#top">トップへ</a>)</div>

## トラブルシューティング

### フロントエンドの dev サーバが起動しない（npm run dev が失敗）

- Node.js のバージョンを確認（推奨: 18 以上）。
- `VITE_API_BASE_URL` が未設定だと API 呼び出しが失敗します。`.env` や VS Code の Launch 設定等で設定してください。
- ポート競合の可能性（`5173`）。他のプロセスを停止するか、Vite のポート設定を変更してください。

### Webhook で署名検証エラーになる

- `STRIPE_WEBHOOK_SECRET`（signing secret）が一致しているか確認。
- Stripe CLI で転送している URL とアプリの受け口（例: `/stripe/webhook`）が一致しているか確認。

### 購読状態が反映されない

- Webhook の配信状況を Stripe ダッシュボードで確認。
- 翌日の Reconciliation により補正されます。すぐに確認したい場合は一時的に手動トリガーを用意してください（開発向け）。

<div align="right">(<a href="#top">トップへ</a>)</div>

## 貢献

Issue/Pull Request を歓迎します。開発方針・Mapper の命名規約・Stripe のドメインルールに沿ってください。

## ライセンス

プロジェクトルートの `LICENSE` を参照してください（未設定の場合は社内利用前提）。

<div align="right">(<a href="#top">トップへ</a>)</div>
