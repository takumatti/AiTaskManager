import type { Task } from "../../types/task";
import "./TaskItem.css";

const formatDate = (dateStr?: string) => {
  if (!dateStr) return "なし";
  // 8桁数字（例: 20251124）
  if (/^\d{8}$/.test(dateStr)) {
    return `${dateStr.slice(0,4)}/${dateStr.slice(4,6)}/${dateStr.slice(6,8)}`;
  }
  // ISO8601やYYYY-MM-DD/スラッシュ区切り
  const m = dateStr.match(/(\d{4})[-/](\d{2})[-/](\d{2})/);
  if (m) return `${m[1]}/${m[2]}/${m[3]}`;
  // 先頭10文字が日付ならそれを
  if (dateStr.length >= 10) {
    const ymd = dateStr.slice(0, 10).replace(/-/g, "/");
    if (/\d{4}\/\d{2}\/\d{2}/.test(ymd)) return ymd;
  }
  return dateStr;
};

export const TaskItem = ({
  task,
  onDelete,
}: {
  task: Task;
  onDelete: (id: number) => void;
}) => {
  return (
    <div className="task-item-card">
      <div className="task-item-header">
        <div className="task-item-title">{task.title}</div>
        <button
          className="task-delete-btn"
          onClick={() => onDelete(task.id)}
        >
          削除
        </button>
      </div>

      {task.description && (
        <div className="task-item-desc">{task.description}</div>
      )}

      <div className="task-meta">
        <span>期限: {formatDate(task.due_date)}</span>
        <span>優先度: {task.priority}</span>
        <span>ステータス: {task.status}</span>
      </div>
    </div>
  );
};