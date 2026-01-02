package com.aitaskmanager.service.ai;

import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OpenAIを利用したタスク関連のサービスクラス
 */
@Service
@Slf4j
public class OpenAiTaskService {

    @Value("${openai.enabled:false}")
    private boolean enabled;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    private final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient http = HttpClient.newHttpClient();

    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /**
     * タスクのタイトルと説明から子タスク案を生成する
     * 
     * @param title       親タスクのタイトル
     * @param description 親タスクの説明
     * @param dueDate     親タスクの期日（任意）
     * @param priority    親タスクの優先度（任意）
     * @return 生成された子タスクのリスト
     */
    public List<SubTask> generateSubTasks(String title, String description, String dueDate, String priority) {
        long startNs = System.nanoTime();
        List<SubTask> results = new ArrayList<>();
        String base = (description != null && !description.isBlank()) ? description : title;
        if (base == null) base = "";

        // 開始ログ（入力の長さと語数）
        int words = base.trim().isEmpty() ? 0 : base.trim().split("\\s+").length;
        log.info("[OpenAiTaskService] generateSubTasks start len={} words={} titleLen={} descLen={}", base.length(), words, (title != null ? title.length() : 0), (description != null ? description.length() : 0));

        // 曖昧スキップ（プレビューでは空リスト返却）
        int minChars = 20;
        int minWords = 3;
        if (base.trim().isEmpty() || (base.length() < minChars && words < minWords)) {
            log.info("[OpenAiTaskService] skip due to ambiguous input (len<{} && words<{})", minChars, minWords);
            log.info("[OpenAiTaskService] generateSubTasks end items=0 elapsedMs={}", (System.nanoTime() - startNs) / 1_000_000);
            return results;
        }

        String prompt = "あなたはタスク分解のアシスタントです。以下の親タスクの説明から、タスクが達成できるように細かい子タスク案を JSON で返してください（タスクが達成できるようにできるだけ細かくして返してください）。\n"
            + "親タイトル: " + safe(title) + "\n"
            + "説明: " + safe(description) + "\n"
            + (dueDate != null ? ("期日: " + dueDate + "\n") : "")
            + (priority != null ? ("優先度: " + priority + "\n") : "")
            + "必須条件: タイトルと説明の値は必ず日本語で出力するようにしてください。\n"
            + "出力形式は厳密に次のみ。キーは英語 (children/title/description)、値は日本語: {\"children\":[{\"title\":\"...\",\"description\":\"...\"}]}";

        try {
        // Chat Completions API に対して system + user の2メッセージ構成、JSON強制の response_format を指定
        var rootBody = mapper.createObjectNode();
        rootBody.put("model", model);
        var messages = rootBody.putArray("messages");
        messages.add(mapper.createObjectNode()
            .put("role", "system")
            .put("content", "You output strictly a single JSON object. All values must be in Japanese. Keys must be in English (children/title/description). No extra text."));
        messages.add(mapper.createObjectNode()
            .put("role", "user")
            .put("content", prompt));
        rootBody.put("temperature", 0);
        // OpenAIの新仕様では JSON を強制するために response_format が有効（サポートモデル限定）
        var responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_object");
        rootBody.set("response_format", responseFormat);
        String body = rootBody.toString();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                JsonNode root = mapper.readTree(resp.body());
                String content = root.path("choices").path(0).path("message").path("content").asText("");
                if (content != null && !content.isBlank()) {
                    // contentがJSONのはずなのでパースを試みる
                    JsonNode json = tryParseJson(content);
                    if (json != null) {
                        JsonNode children = json.path("children");
                        if (children.isArray()) {
                            for (JsonNode c : children) {
                                String ct = c.path("title").asText("");
                                String cd = c.path("description").asText("");
                                if (!ct.isBlank()) {
                                    results.add(new SubTask(ct, cd));
                                }
                            }
                            // 正規化して親説明/タイトルと同一のものを除外
                            String normParentDesc = normalize(description);
                            String normParentTitle = normalize(title);
                            List<SubTask> filtered = new ArrayList<>();
                            for (SubTask st : results) {
                                String nt = normalize(st.title);
                                String nd = normalize(st.description);
                                boolean dupWithDesc = !normParentDesc.isEmpty() && (nt.equalsIgnoreCase(normParentDesc) || nd.equalsIgnoreCase(normParentDesc));
                                boolean dupWithTitle = !normParentTitle.isEmpty() && (nt.equalsIgnoreCase(normParentTitle) || nd.equalsIgnoreCase(normParentTitle));
                                if (!dupWithDesc && !dupWithTitle) {
                                    filtered.add(st);
                                }
                            }
                            results = filtered;
                            // 最大件数の上限（大きめに許容）
                            int maxItems = 50;
                            if (results.size() > maxItems) {
                                results = results.subList(0, maxItems);
                            }
                        }
                    }
                }
            } else {
                // ステータスとエラーメッセージをログに出す
                log.warn("[OpenAiTaskService] OpenAI chat completion failed status={} body={}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            // 失敗時は空リストを返す
            log.warn("[OpenAiTaskService] generateSubTasks failed: {}", e.toString());
            log.info("[OpenAiTaskService] generateSubTasks end items=0 elapsedMs={}", (System.nanoTime() - startNs) / 1_000_000);
            return results;
        }
        log.info("[OpenAiTaskService] generateSubTasks end items={} elapsedMs={}", results.size(), (System.nanoTime() - startNs) / 1_000_000);
        return results;
    }

    /**
     * 安全に文字列を取得する（nullを空文字に変換）
     * 
     * @param s 入力文字列
     * @return 安全な文字列
     */
    private String safe(String s) {
        return s == null ? "" : s;
    }


    /**
     * JSONパースを試みる
     * 
     * @param s パース対象の文字列
     * @return JsonNodeオブジェクト、パース失敗時はnull
     */
    private JsonNode tryParseJson(String s) {
        try {
            return mapper.readTree(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 重複判定のための簡易正規化
     */
    private String normalize(String s) {
        if (s == null) return "";
        String t = s.trim()
                .replaceFirst("^[\\-\\*]\\s*", "")
                .replaceFirst("^•\\s*", "")
                .replaceAll("\\s+", " ");
        return t.toUpperCase();
    }

    /**
     * サブタスク案を表す内部クラス
     */
    public static class SubTask {
        public final String title;
        public final String description;
        public SubTask(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }
}
