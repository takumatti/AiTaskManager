package com.aitaskmanager.service.ai;

import org.springframework.beans.factory.annotation.Value;
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
public class OpenAiTaskService {

    @Value("${openai.enabled:false}")
    private boolean enabled;

    @Value("${openai.apiKey:}")
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
        List<SubTask> results = new ArrayList<>();
        String base = (description != null && !description.isBlank()) ? description : title;
        if (base == null || base.isBlank()) {
            return results;
        }

        String prompt = "あなたはタスク分解のアシスタントです。以下の親タスクの説明から3件前後の子タスク案をJSONで返してください。\n" +
                "親タイトル: " + safe(title) + "\n" +
                "説明: " + safe(description) + "\n" +
                (dueDate != null ? ("期日: " + dueDate + "\n") : "") +
                (priority != null ? ("優先度: " + priority + "\n") : "") +
                "出力は次の形式のみで、キーは英語にしてください: {\"children\":[{\"title\":\"...\",\"description\":\"...\"}]}";

        try {
            String body = mapper.createObjectNode()
                    .put("model", model)
                    .putArray("messages")
                        .add(mapper.createObjectNode()
                                .put("role", "user")
                                .put("content", prompt))
                    .toString();

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
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 失敗時は空リストを返す
            return results;
        }
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
