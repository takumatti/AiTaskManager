package com.aitaskmanager.repository.dto.tasks;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 階層構造でタスクを返すDTO (親→子→孫... 再帰)
 */
@Data
public class TaskTreeResponse {
    /** タスクID */
    private Integer id;
    /** ユーザーID */
    private Integer userId;
    /** 親タスクID */
    private Integer parentTaskId;
    /** タスクタイトル */
    private String title;
    /** タスク詳細 */
    private String description;
    /** タスク期限日 */
    private String dueDate; // yyyy/MM/dd
    /** タスク優先度 */
    private String priority;
    /** タスクステータス */
    private String status;
    /** タスク作成日時 */
    private String createdAt;
    /** タスク更新日時 */
    private String updatedAt;
    /** タスク細分化日時 */
    private String decomposedAt;
    /** 子タスクのリスト */
    private List<TaskTreeResponse> children = new ArrayList<>();
}
