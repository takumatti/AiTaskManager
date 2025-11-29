import type { Task } from "../../types/task";
import "./TaskItem.css";
import { useState } from "react";
import { ConfirmDialog } from "../common/ConfirmDialog";

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
  const [confirmOpen, setConfirmOpen] = useState(false);
  const handleDeleteClick = () => setConfirmOpen(true);
  const handleConfirm = () => { setConfirmOpen(false); onDelete(task.id); };
  const handleCancel = () => setConfirmOpen(false);
  return (
    <div className="task-item-card">
      <div className="task-item-header">
        <div className="task-item-title">{task.title}</div>
        <div className="task-item-actions">
          <button className="task-edit-btn" onClick={() => onEdit(task)}>編集</button>
          <button
            className="task-delete-btn"
            onClick={handleDeleteClick}
          >
            削除
          </button>
        </div>
      </div>

      <ConfirmDialog
        open={confirmOpen}
        title="削除の確認"
        message={`タスク「${task.title}」を削除します。よろしいですか？`}
        confirmText="削除"
        cancelText="キャンセル"
        onConfirm={handleConfirm}
        onCancel={handleCancel}
      />

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
