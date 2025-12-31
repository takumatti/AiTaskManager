package com.aitaskmanager.repository.dto.ai;

import java.util.List;

import lombok.Data;

/**
 * タスク細分化DTO
 */
@Data
public class TaskBreakdownDTO {
    /** リクエストDTO */
    public static class Request {
        /** 細分化するタスクのタイトル */
        public String title;
        /** 細分化するタスクの詳細説明 */
        public String description;
        /** 締め切り日（YYYY-MM-DD形式、任意） */
        public String dueDate;
        /** 優先度（HIGH/NORMAL/LOW、任意） */
        public String priority;
    }

    /** サブタスクDTO */
    public static class SubTask {
        /** サブタスクのタイトル */
        public String title;
        /** サブタスクの詳細説明 */
        public String description;
    }

    /** レスポンスDTO */
    public static class Response {
        /** サブタスクの警告メッセージ（あれば） */
        public String warning;
        /** サブタスクのリスト */
        public List<SubTask> children;
    }
}
