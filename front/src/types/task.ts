export type TaskStatus = "TODO" | "DOING" | "DONE";
export type TaskPriority = "LOW" | "NORMAL" | "HIGH";

export interface Task {
  id: number;
  userId: number;
  parentTaskId?: number | null;
  title: string;
  description?: string;
  dueDate?: string; // YYYY-MM-DD形式
  priority: TaskPriority;
  status: TaskStatus;
  createdAt: string;
  updatedAt: string;
}

export interface TaskInput {
  title: string;
  description?: string;
  status: string;
  due_date?: string; // API契約維持のため送信はsnake_case
  priority?: string;
  ai_decompose?: boolean;
  ai_generated?: boolean;
  // 親タスクID（手動子作成用、サーバは snake/camel 両対応）
  parent_task_id?: number;
  parentTaskId?: number;
}