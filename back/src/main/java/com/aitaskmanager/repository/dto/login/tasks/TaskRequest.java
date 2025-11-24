package com.aitaskmanager.repository.dto.login.tasks;

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
    /** タスクの優先度 */
    private String priority;
}