import type { Task } from "../../types/task";
import { TaskItem } from "./TaskItem";

// タスク一覧コンポーネント
export const TaskList = ({
  tasks,
  onDelete,
  onEdit,
}: {
  tasks: Task[];
  onDelete: (id: number) => void;
  onEdit: (task: Task) => void;
}) => {
  if (tasks.length === 0) {
    return <p>タスクがありません</p>;
  }

  return (
    <div className="task-list">
      {tasks.map((task) => (
        <TaskItem key={task.id} task={task} onDelete={onDelete} onEdit={onEdit} />
      ))}
    </div>
  );
};