interface Props {
  status: string;
  priority: string;
  onStatusChange: (value: string) => void;
  onPriorityChange: (value: string) => void;
  showStatus?: boolean;
  showPriority?: boolean;
}

export const TaskFilters = ({
  status,
  priority,
  onStatusChange,
  onPriorityChange,
  showStatus = true,
  showPriority = true
}: Props) => {
  return (
    <div className="task-filters">
      {showStatus && (
        <select value={status} onChange={(e) => onStatusChange(e.target.value)}>
          <option value="">すべてのステータス</option>
          <option value="TODO">未着手</option>
          <option value="DOING">進行中</option>
          <option value="DONE">完了</option>
        </select>
      )}
      {showPriority && (
        <select value={priority} onChange={(e) => onPriorityChange(e.target.value)}>
          <option value="">すべての優先度</option>
          <option value="HIGH">高</option>
          <option value="NORMAL">中</option>
          <option value="LOW">低</option>
        </select>
      )}
    </div>
  );
};
