import React from 'react';
import type { Task } from '../../types/task';
import './TaskCalendar.css';

/**
 * タスクツールチップコンポーネント
 */
interface TaskTooltipProps {
  task: Task;
  overdue: boolean;
  onEdit: (task: Task) => void;
  onClose: () => void;
}

// タスクツールチップコンポーネント
export const TaskTooltip: React.FC<TaskTooltipProps> = ({ task, overdue, onEdit, onClose }) => {
  const statusJa: Record<string,string> = { TODO: '未着手', DOING: '進行中', DONE: '完了' };
  const priorityJa: Record<string,string> = { HIGH: '高', NORMAL: '中', LOW: '低' };
  return (
    <div className={`task-tooltip${overdue ? ' overdue' : ''}`}>      
      <div className="task-tooltip-header">
        <strong className="task-tooltip-title" title={task.title}>{task.title}</strong>
        <button type="button" className="btn-close" aria-label="閉じる" onClick={onClose}></button>
      </div>
      <div className="task-tooltip-body">
        {task.due_date && (
          <div className="tt-row">
            <span className="tt-label">期日:</span>
            <span className="tt-value">{task.due_date}{overdue && <span className="badge-overdue ms-1">期限超過</span>}</span>
          </div>
        )}
        <div className="tt-row">
          <span className="tt-label">ステータス:</span>
          <span className={`tt-badge status-${task.status.toLowerCase()}`}>{statusJa[task.status] || task.status}</span>
        </div>
        <div className="tt-row">
          <span className="tt-label">優先度:</span>
          <span className={`tt-badge priority-${task.priority.toLowerCase()}`}>{priorityJa[task.priority] || task.priority}</span>
        </div>
        {task.description && (
          <div className="tt-row description">
            <span className="tt-label">説明:</span>
            <span className="tt-description" title={task.description}>{task.description}</span>
          </div>
        )}
      </div>
      <div className="task-tooltip-footer">
        <button className="btn btn-sm btn-primary" onClick={() => onEdit(task)}>編集</button>
      </div>
    </div>
  );
};