package com.aitaskmanager.service.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI APIを使用してプロジェクト説明を具体的なタスクに分解するサービス
 */
@Service
@Slf4j
public class OpenAiDecomposeService {

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    // 設定値（将来的にapplication.propertiesから外出し可能）
    @Value("${openai.timeoutSeconds:20}")
    private int TIMEOUT_SECONDS; // タイムアウト
    @Value("${openai.maxRetries:2}")
    private int MAX_RETRIES; // 失敗時のリトライ回数
    @Value("${openai.initialBackoffMs:500}")
    private long INITIAL_BACKOFF_MS; // バックオフ初期値
    // 簡易サーキットブレーカー
    @Value("${openai.circuit.failureThreshold:3}")
    private int CB_FAILURE_THRESHOLD; // 連続失敗でオープン
    @Value("${openai.circuit.openDurationMs:30000}")
    private long CB_OPEN_DURATION_MS; // オープン維持時間

    @Value("${openai.apiKey:}")
    private String configuredApiKey;

    private final HttpClient http = HttpClient.newHttpClient();
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenedAt = new AtomicLong(0);

    /**
     * プロジェクト説明を具体的なタスクに分解する
     * 
     * @param description プロジェクト説明
     * @return 分解されたタスクのリスト（失敗時は空リスト）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> decompose(String description) {
        String apiKey = configuredApiKey;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[OpenAiDecomposeService] OPENAI_API_KEY not set; returning empty result");
            return List.of();
        }
        // サーキットブレーカー: オープン期間中は即座に失敗扱い
        long openedAt = circuitOpenedAt.get();
        if (openedAt > 0 && (System.currentTimeMillis() - openedAt) < CB_OPEN_DURATION_MS) {
            log.warn("[OpenAiDecomposeService] circuit open; skipping call");
            return List.of();
        }
        try {
            String prompt = buildPrompt(description);
            String body = "{\n" +
                "  \"model\": \"gpt-4o-mini\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"system\", \"content\": \"You are an assistant that decomposes a project description into concrete TODO tasks. Return a pure JSON array of strings in Japanese.\"},\n" +
                "    {\"role\": \"user\", \"content\": " + jsonString(prompt) + "}\n" +
                "  ],\n" +
                "  \"temperature\": 0.2\n" +
                "}";
            int attempt = 0;
            long backoff = INITIAL_BACKOFF_MS;
            while (true) {
                attempt++;
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

                try {
                    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (res.statusCode() == 200) {
                        consecutiveFailures.set(0); // 成功でリセット
                        // 簡易抽出: 最終メッセージのcontentからJSON配列を抜き出す
                        String content = extractContent(res.body());
                        List<String> items = parseJsonArrayOfStrings(content);
                        return items;
                    }
                    log.warn("[OpenAiDecomposeService] non-200 attempt={} status={} body={}", attempt, res.statusCode(), truncate(res.body()));
                } catch (Exception callEx) {
                    log.warn("[OpenAiDecomposeService] call error attempt={} ex={}", attempt, callEx.toString());
                }

                // リトライ判定
                if (attempt > MAX_RETRIES) {
                    int failures = consecutiveFailures.incrementAndGet();
                    if (failures >= CB_FAILURE_THRESHOLD) {
                        circuitOpenedAt.compareAndSet(0, System.currentTimeMillis());
                        log.warn("[OpenAiDecomposeService] circuit opened due to consecutive failures={}", failures);
                    }
                    return List.of();
                }

                // バックオフして再試行
                try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                backoff = Math.min(backoff * 2, 5_000); // 最大5秒まで増加
            }
        } catch (Exception ex) {
            log.warn("[OpenAiDecomposeService] call failed: {}", ex.getMessage());
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= CB_FAILURE_THRESHOLD) {
                circuitOpenedAt.compareAndSet(0, System.currentTimeMillis());
                log.warn("[OpenAiDecomposeService] circuit opened due to consecutive failures={}", failures);
            }
            return List.of();
        }
    }

    /** プロンプト文を構築する 
     * 
     * @param description プロジェクト説明
     * @return プロンプト文字列
     */
    private String buildPrompt(String description) {
        return "次の説明から、実行可能な開発タスクに日本語で分解してください。箇条書きにし、各要素は短い命令文1行で。JSON配列(文字列のみ)で返してください。説明:" + description;
    }

    /** JSON文字列エスケープ用ユーティリティ
     * 
     * @param str 元文字列
     * @return エスケープ済みJSON文字列
     */
    private static String jsonString(String str) {
        return '"' + str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }

    /** 長い文字列を切り詰めるユーティリティ（ログ用） 
     * 
     * @param str 元文字列
     * @return 切り詰めた文字列
     */
    private static String truncate(String str) {
        if (str == null) return null;
        return str.length() > 500 ? str.substring(0, 500) + "..." : str;
    }

    /** レスポンスボディからcontentフィールドを抽出する（簡易実装） 
     * 
     * @param body レスポンスボディ文字列
     * @return contentフィールドの値
     */
    private String extractContent(String body) {
        int idx = body.lastIndexOf("\"content\":");
        if (idx < 0) return body;
        String sub = body.substring(idx + 10);
        int start = sub.indexOf('"');
        int end = sub.indexOf("\"", start + 1);
        if (start >= 0 && end > start) return sub.substring(start + 1, end);
        return body;
    }

    /** contentフィールドからJSON配列を解析する（簡易実装） 
     * 
     * @param content contentフィールドの値
     * @return タスク文字列のリスト
     */
    private List<String> parseJsonArrayOfStrings(String content) {
        List<String> list = new ArrayList<>();
        int l = content.indexOf('[');
        int r = content.lastIndexOf(']');
        if (l >= 0 && r > l) {
            String inner = content.substring(l + 1, r);
            for (String part : inner.split(",")) {
                String t = part.trim();
                if (t.startsWith("\"") && t.endsWith("\"")) {
                    t = t.substring(1, t.length() - 1);
                }
                t = t.replace("\\\"", "\"").replace("\\n", " ").trim();
                if (!t.isBlank()) list.add(t);
            }
        }
        return list;
    }
}
