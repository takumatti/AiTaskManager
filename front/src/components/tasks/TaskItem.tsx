import type { Task } from "../../types/task";
import "./TaskItem.css";

const formatDate = (dateStr?: string) => {
  if (!dateStr) return "なし";
  // DBはDate型（yyyy-MM-dd等）→東京タイムゾーンでYYYY/MM/DD表示
  // 例: "2025-12-02" → "2025/12/02"
  const m = dateStr.match(/(\d{4})-(\d{2})-(\d{2})/);
  if (m) {
    // Date型としてパースしJSTで表示
    const dt = new Date(`${m[1]}-${m[2]}-${m[3]}T00:00:00+09:00`);
    const y = dt.getFullYear();
    const mon = String(dt.getMonth() + 1).padStart(2, "0");
    const d = String(dt.getDate()).padStart(2, "0");
    return `${y}/${mon}/${d}`;
  }
  return dateStr;
};

// タスクアイテムコンポーネント
export const TaskItem = ({
  task,
  onDelete,
  onEdit,
}: {
  task: Task;
  onDelete: (id: number) => void;
  onEdit: (task: Task) => void;
}) => {
  return (
    <div className="task-item-card">
      <div className="task-item-header">
        <div className="task-item-title">{task.title}</div>
        {/* 編集ボタン */}
        <button onClick={() => onEdit(task)}>編集</button>
        {/* 削除ボタン */}
        <button className="task-delete-btn" onClick={() => onDelete(task.id)}>
          削除
        </button>
      </div>

      {task.description && (
        <div className="task-item-desc">{task.description}</div>
      )}

      <div className="task-meta">
        <span>期限: {formatDate(task.due_date)}</span>
        <span>優先度: {(() => {
          switch (task.priority) {
            case "HIGH": return "高";
            case "NORMAL": return "中";
            case "LOW": return "低";
            default: return task.priority;
          }
        })()}</span>
        <span>ステータス: {(() => {
          switch (task.status) {
            case "TODO": return "未着手";
            case "DOING": return "進行中";
            case "DONE": return "完了";
            default: return task.status;
          }
        })()}</span>
      </div>
    </div>
  );
};
