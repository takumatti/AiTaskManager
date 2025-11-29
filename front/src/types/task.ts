export type TaskStatus = "TODO" | "DOING" | "DONE";
export type TaskPriority = "LOW" | "NORMAL" | "HIGH";

export interface Task {
  id: number;
  user_id: number;
  title: string;
  description?: string;
  due_date?: string; // YYYY-MM-DD形式
  priority: TaskPriority;
  status: TaskStatus;
  created_at: string;
  updated_at: string;
}

export interface TaskInput {
  title: string;
  description?: string;
  status: string;
  due_date?: string;
  priority?: string;
}