package com.aitaskmanager.service.ai;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring AI を利用してプロジェクト説明を具体的なタスクに分解するサービス
 */
@Service
@Slf4j
public class OpenAiDecomposeService {

    private Object chatModel;

    /**
     * コンストラクタ - Spring AI の ChatModel をリフレクションで解決を試みる
     * 
     * @param applicationContext Springのアプリケーションコンテキスト
     */
    public OpenAiDecomposeService(ApplicationContext applicationContext) {
        Object resolved = null;
        // Prefer resolving via ChatModel interface
        try {
            Class<?> chatModelInterface = Class.forName("org.springframework.ai.chat.model.ChatModel");
            resolved = applicationContext.getBean(chatModelInterface);
            log.info("[OpenAiDecomposeService] Resolved ChatModel bean via interface: {}", resolved.getClass().getName());
        } catch (Throwable t) {
            log.info("[OpenAiDecomposeService] ChatModel interface bean not found: {}", t.toString());
        }
        // Fallback: try specific OpenAiChatModel implementation
        if (resolved == null) {
            try {
                Class<?> openAiModelClass = Class.forName("org.springframework.ai.openai.OpenAiChatModel");
                resolved = applicationContext.getBean(openAiModelClass);
                log.info("[OpenAiDecomposeService] Resolved OpenAiChatModel bean: {}", resolved.getClass().getName());
            } catch (Throwable t) {
                log.info("[OpenAiDecomposeService] OpenAiChatModel bean not found: {}", t.toString());
            }
        }
        this.chatModel = resolved;
        if (this.chatModel == null) {
            log.info("[OpenAiDecomposeService] Spring AI ChatModel not available; will use fallback parsing.");
        }
    }

    /**
     * プロジェクト説明を具体的なタスクに分解する
     * 
     * @param description プロジェクト説明
     * @return 分解されたタスクのリスト（失敗時は空リスト）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> decompose(String description) {
        log.info("[OpenAiDecomposeService] decompose start chatModelPresent={} descLen={}", (this.chatModel != null), (description != null ? description.length() : 0));
        List<String> items = new ArrayList<>();
        try {
            String content = buildPrompt(description);
            String text = callSpringAi(content);
            if (text != null) {
                items.addAll(parseToList(text));
            } else {
                // Fallback parsing if AI unavailable or returned null
                items.addAll(parseToList(description == null ? "" : description));
            }
        } catch (Exception e) {
            log.warn("[OpenAiDecomposeService] AI call failed; using fallback: {}", e.toString());
            items.addAll(parseToList(description == null ? "" : description));
        }
        log.info("[OpenAiDecomposeService] decompose end items={}", items.size());
        return items;
    }

    /**
     * プロンプト文を構築する
     * 
     * @param description プロジェクト説明
     * @return プロンプト文
     */
    private String buildPrompt(String description) {
        return "次の説明から、実行可能な開発タスクに分解してください。" +
               "説明は日本語で出力するようにしてください。" +
               "箇条書きで返し、各要素は短い命令文1行。説明:" + description;
    }

    /**
     * テキストを箇条書きのリストにパースする
     * 
     * @param text 入力テキスト
     * @return 箇条書きのリスト
     */
    private List<String> parseToList(String text) {
        List<String> list = new ArrayList<>();
        String[] lines = text.split("\r?\n");
        for (String line : lines) {
            String t = line.trim().replaceFirst("^[\\-\\*]\\s*", "");
            if (!t.isBlank()) list.add(t);
            if (list.size() >= 10) break; // 上限の安全弁
        }
        return list;
    }

    /**
     * Spring AI の ChatModel をリフレクションで呼び出す
     * 
     * @param content プロンプト内容
     * @return AIの応答テキスト、失敗時はnull
     */
    private String callSpringAi(String content) {
        if (chatModel == null) {
            log.info("[OpenAiDecomposeService] callSpringAi skipped: chatModel is null (fallback will be used)");
            return null;
        }
        try {
            Class<?> userMessageClass = Class.forName("org.springframework.ai.chat.messages.UserMessage");
            Object userMessage = userMessageClass.getConstructor(String.class).newInstance(content);

            Class<?> promptClass = Class.forName("org.springframework.ai.chat.prompt.Prompt");
            Object prompt = promptClass.getConstructor(userMessageClass).newInstance(userMessage);

            Method callMethod = chatModel.getClass().getMethod("call", promptClass);
            Object chatResponse = callMethod.invoke(chatModel, prompt);

            // Navigate: resp.getResult().getOutput().getContent()
            Method getResult = chatResponse.getClass().getMethod("getResult");
            Object result = getResult.invoke(chatResponse);

            Method getOutput = result.getClass().getMethod("getOutput");
            Object output = getOutput.invoke(result);

            Method getContent = output.getClass().getMethod("getContent");
            Object textObj = getContent.invoke(output);
            return textObj == null ? null : textObj.toString();
        } catch (Throwable t) {
            log.warn("[OpenAiDecomposeService] Reflection call failed: {}", t.toString());
            return null;
        }
    }
}
