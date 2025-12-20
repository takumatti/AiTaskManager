package com.aitaskmanager.repository.dto.tasks;

import lombok.Data;

/**
 * タスク作成リクエストのDTO
 */
@Data
public class TaskRequest {
    /** タスクのタイトル */
    private String title;
    /** タスクの説明 */
    private String description;
    /** タスクの期限日 */
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