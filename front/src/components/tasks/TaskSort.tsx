interface Props {
  sort: string;
  onSortChange: (value: string) => void;
}

export const TaskSort = ({ sort, onSortChange }: Props) => {
  return (
    <div className="task-sort">
      <select value={sort} onChange={(e) => onSortChange(e.target.value)}>
        <option value="due_date_asc">期限日が近い順</option>
        <option value="due_date_desc">期限日が遠い順</option>
        <option value="created_desc">新しい順</option>
        <option value="created_asc">古い順</option>
      </select>
    </div>
  );
};