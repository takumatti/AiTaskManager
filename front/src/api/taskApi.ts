import apiClient from "./apiClient";
import type { TaskInput, Task } from "../types/task";

const API_BASE = "/api/tasks";

// タスク一覧取得API
export const fetchTasks = async (): Promise<Task[]> => {
  const res = await apiClient.get<Task[]>(API_BASE);
  return res.data;
};

// タスク作成API
export const createTask = async (task: TaskInput): Promise<Task> => {
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