package com.aitaskmanager.repository.dto.login.tasks;

import lombok.Data;

/**
 * タスク取得レスポンス用DTO
 */
@Data
public class TaskResponse {
    /** タスクID */
    private Integer id;
    /** ユーザーID */
    private Integer userId;
    /** 親タスクID（nullならルート） */
    private Integer parentTaskId;
    /** タイトル */
    private String title;
    /** 説明 */
    private String description;
    /** 期限日 */
    private String dueDate; // "YYYY/MM/DD" 形式
    /** 優先度 */
    private String priority;
    /** ステータス */
    private String status;
    /** 作成日時 */
    private String createdAt;
    /** 更新日時 */
    private String updatedAt;
}
