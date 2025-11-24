package com.aitaskmanager.repository.dto.login.tasks;

import lombok.Data;

/**
 * タスク取得レスポンス用DTO
 */
@Data
public class TaskResponse {
    private Integer id;
    private Integer user_id;
    private String title;
    private String description;
    private String due_date; // "YYYY/MM/DD" 形式
    private String priority;
    private String status;
    private String created_at;
    private String updated_at;
}
