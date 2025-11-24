import type { Task } from "../../types/task";
import { TaskItem } from "./TaskItem";

export const TaskList = ({ tasks }: { tasks: Task[] }) => {
  if (tasks.length === 0) {
    return <p>タスクがありません</p>;
  }

  return (
    <div className="task-list">
      {tasks.map(task => (
        <TaskItem key={task.id} task={task} />
      ))}
    </div>
  );
};