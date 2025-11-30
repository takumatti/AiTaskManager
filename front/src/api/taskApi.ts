import apiClient from "./apiClient";
import type { TaskInput, Task } from "../types/task";

// 生データ型（APIレスポンス用）
interface RawTask {
  id: number;
  user_id?: number;
  userId?: number;
  parent_task_id?: number | null;
  parentTaskId?: number | null;
  title: string;
  description?: string;
  due_date?: string;
  dueDate?: string;
  priority: string;
  status: string;
  created_at?: string;
  updated_at?: string;
  createdAt?: string;
  updatedAt?: string;
}

const API_BASE = "/api/tasks";

// タスク一覧取得API
export const fetchTasks = async (): Promise<Task[]> => {
  const res = await apiClient.get<RawTask[]>(API_BASE);
  // 正規化: snake / camel 両対応、最終的に Task 型へフィット
  return res.data.map(raw => {
    const parentTaskId = raw.parentTaskId ?? raw.parent_task_id ?? null;
    const userId = raw.userId ?? raw.user_id!;
    const dueDate = raw.dueDate ?? raw.due_date;
    const createdAt = raw.createdAt ?? raw.created_at!;
    const updatedAt = raw.updatedAt ?? raw.updated_at!;
    return {
      id: raw.id,
      userId,
      parentTaskId,
      title: raw.title,
      description: raw.description,
      dueDate,
      priority: raw.priority as Task["priority"],
      status: raw.status as Task["status"],
      createdAt,
      updatedAt,
    } as Task;
  });
};

// 階層ツリー取得
export interface TaskTreeNode {
  id: number;
  userId: number;
  parentTaskId: number | null;
  title: string;
  description?: string;
  dueDate?: string;
  priority: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  decomposedAt?: string | null;
  children: TaskTreeNode[];
}

// タスクツリー取得API
export const fetchTaskTree = async (): Promise<TaskTreeNode[]> => {
  const res = await apiClient.get<TaskTreeNode[]>(`${API_BASE}/tree`);
  return res.data;
};


// 細分化API
export const decomposeTask = async (id: number, input: Partial<TaskInput>): Promise<TaskTreeNode[]> => {
  const res = await apiClient.post<TaskTreeNode[]>(`${API_BASE}/${id}/decompose`, input);
  return res.data;
};

// タスク作成API
export const createTask = async (task: TaskInput): Promise<Task> => {
  // ai_decompose フラグなど拡張フィールドも TaskInput 経由で送信
  const res = await apiClient.post<Task>(API_BASE, task);
  return res.data;
};

// タスク更新API
export const updateTask = async (taskId: number, task: TaskInput): Promise<Task> => {
  const res = await apiClient.put<Task>(`${API_BASE}/${taskId}`, task);
  return res.data;
};

// タスク削除API
export const deleteTask = async (taskId: number): Promise<void> => {
  await apiClient.delete(`${API_BASE}/${taskId}`);
};