package com.aitaskmanager.repository.dto.tasks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * タスク作成リクエストのDTO
 */
@Data
public class TaskRequest {
    /** タスクのタイトル */
    @NotBlank(message = "タイトルは必須です")
    private String title;
    /** タスクの説明 */
    private String description;
    /** タスクの期限日（任意）: 入力される場合は yyyy/MM/dd 形式 */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "期限日は yyyy/MM/dd 形式で入力してください")
    private String due_date;
    /** 親タスクID (snake_case) - 新規作成時の手動子タスク追加用 */
    private Integer parent_task_id;
    /** 親タスクID (camelCase エイリアス) */
    private Integer parentTaskId;
    /** タスクの優先度 */
    private String priority;
    /** タスクのステータス */
    private String status;
    /** AIで細分化するか（trueならサブタスク生成を試みる） */
    private Boolean ai_decompose;
}