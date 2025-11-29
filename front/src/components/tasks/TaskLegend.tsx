import React from 'react';
import './TaskCalendar.css';

/**
 * タスクの凡例コンポーネント
 */
export const TaskLegend: React.FC = () => {
  return (
    <div className="task-legend">
      <div className="legend-item"><span className="legend-pill status-todo"/>未着手</div>
      <div className="legend-item"><span className="legend-pill status-doing"/>進行中</div>
      <div className="legend-item"><span className="legend-pill status-done"/>完了</div>
      <div className="legend-item"><span className="legend-pill overdue"/>期限超過</div>
      <div className="legend-item"><span className="legend-pill priority-high"/>高優先度</div>
      <div className="legend-item"><span className="legend-pill priority-low"/>低優先度</div>
    </div>
  );
};