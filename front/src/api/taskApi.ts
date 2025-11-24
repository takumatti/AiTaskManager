import apiClient from "./apiClient";
import type { TaskInput, Task } from "../types/task";

const API_BASE = "/api/tasks";

export const fetchTasks = async (): Promise<Task[]> => {
  const res = await apiClient.get<Task[]>(API_BASE);
  return res.data;
};

export const createTask = async (task: TaskInput): Promise<Task> => {
  const res = await apiClient.post<Task>(API_BASE, task);
  return res.data;
};