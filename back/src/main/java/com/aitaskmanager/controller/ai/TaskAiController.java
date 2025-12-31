package com.aitaskmanager.controller.ai;

import com.aitaskmanager.service.ai.OpenAiTaskService;
import com.aitaskmanager.repository.customMapper.CustomAiUsageMapper;
import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.dto.ai.TaskBreakdownDTO;
import com.aitaskmanager.repository.model.Users;
import com.aitaskmanager.security.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * タスクAIコントローラー
 */
@RestController
@RequestMapping("/api/ai/tasks")
public class TaskAiController {

    @Autowired
    private OpenAiTaskService openAiTaskService;

    @Autowired
    private CustomAiUsageMapper customAiUsageMapper;

    @Autowired
    private UserMapper userMapper;

    // 丁寧版の警告文
    private static final String AMBIGUOUS_WARNING = "入力された説明が抽象的なため、AIで子タスクを自動生成できませんでした。親タスクのみ作成しています。以下を追記すると分解が成功しやすくなります。\n- 目的（なぜやるのか）\n- 具体的な手順（何を、どう進めるのか）\n- 期待する成果物（何が得られれば完了か）\n- 制約（期限・条件・依存関係など）";

    /**
     * タスク細分化エンドポイント
     *
     * @param req タスク細分化リクエストDTO
     * @return タスク細分化レスポンスDTO
     */
    @PostMapping("/breakdown")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskBreakdownDTO.Response> breakdown(@RequestBody TaskBreakdownDTO.Request req) {
        TaskBreakdownDTO.Response resp = new TaskBreakdownDTO.Response();
        resp.children = new ArrayList<>();

        // OpenAIが無効またはAPIキー未設定の場合は警告を返す
        if (!openAiTaskService.isEnabled()) {
            resp.warning = "AI連携が未設定です。管理者に連絡するか、OPENAI_API_KEY を設定してください。";
            return ResponseEntity.ok(resp);
        }

        String title = req.title != null ? req.title.trim() : "";
        String description = req.description != null ? req.description.trim() : "";
        if (description.isBlank() && title.isBlank()) {
            // タイトル・説明が空の場合、親タスク作成フローを阻害しないため早期returnはしない
            resp.warning = AMBIGUOUS_WARNING;
            // OpenAI呼び出しは意味がないので、以降の生成結果は空のまま警告付きで返す
        }

        // 簡易な曖昧判定（短すぎる/語数が少なすぎる場合）
        String base = !description.isBlank() ? description : title;
        int minChars = 20;
        int minWords = 3;
        String[] words = base.trim().split("\\s+");
        if (base.length() < minChars && words.length < minWords) {
            // 警告は付与するが、親タスク作成を阻害しないため早期returnはしない
            resp.warning = AMBIGUOUS_WARNING;
            // OpenAI呼び出しは意味がないので、以降の生成結果は空のまま警告付きで返す
        }

        // OpenAIでサブタスク生成
        List<OpenAiTaskService.SubTask> subs = openAiTaskService.generateSubTasks(title, description, req.dueDate, req.priority);
        for (OpenAiTaskService.SubTask s : subs) {
            TaskBreakdownDTO.SubTask st = new TaskBreakdownDTO.SubTask();
            st.title = s.title;
            st.description = s.description;
            resp.children.add(st);
        }
        // 子提案が0件の場合は親のみ作成の旨を警告として返す
        if (resp.children.isEmpty() && (resp.warning == null || resp.warning.isBlank())) {
            resp.warning = "AIによる子タスク提案がありませんでした。親タスクのみ作成しています。説明をもう少し具体的にすると分解が成功しやすくなります。";
        }
        
        // 成功時（子提案が1件以上）に当月のAI使用回数を+1
        if (!resp.children.isEmpty()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userId = (auth != null) ? AuthUtils.getUserId(auth) : null;
            Users user = (userId != null) ? userMapper.selectByUserId(userId) : null;
            if (user != null && user.getUserSid() != null) {
                java.time.LocalDate now = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo"));
                customAiUsageMapper.upsertIncrement(Math.toIntExact(user.getUserSid()), now.getYear(), now.getMonthValue());
            }
        }
        return ResponseEntity.ok(resp);
    }
}
