import type { Task } from "../../types/task";
import { TaskItem } from "./TaskItem";

export const TaskList = ({
  tasks,
  onDelete,
}: {
  tasks: Task[];
  onDelete: (id: number) => void;
}) => {
  if (tasks.length === 0) {
    return <p>タスクがありません</p>;
  }

  return (
    <div className="task-list">
      {tasks.map((task) => (
        <TaskItem key={task.id} task={task} onDelete={onDelete} />
      ))}
    </div>
  );
};