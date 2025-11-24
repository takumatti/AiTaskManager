import { useState } from "react";
import { createTask } from "../../api/taskApi";
import type { TaskInput } from "../../types/task";

interface Props {
  onCreated: () => void; // 登録完了後に一覧を再取得する
  onClose: () => void;
}

export const TaskCreateForm = ({ onCreated, onClose }: Props) => {
  const [form, setForm] = useState<TaskInput>({
    title: "",
    description: "",
    due_date: "",
    priority: "NORMAL",
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    try {
      await createTask(form);
      onCreated();
      onClose();
    } catch (e) {
      console.error(e);
      alert("タスク登録に失敗しました")
    }
  };

  return (
    <div style={{ border: "1px solid #ddd", padding: 20, borderRadius: 8 }}>
      <h3>タスク追加</h3>
      <form onSubmit={handleSubmit}>
        <div>
          <label>タイトル</label>
          <input name="title" value={form.title} onChange={handleChange} required />
        </div>

        <div>
          <label>説明</label>
          <textarea name="description" value={form.description} onChange={handleChange} />
        </div>

        <div>
          <label>期限</label>
          <input type="date" name="due_date" value={form.due_date} onChange={handleChange} />
        </div>

        <div>
          <label>優先度</label>
          <select name="priority" value={form.priority} onChange={handleChange}>
            <option value="LOW">LOW</option>
            <option value="NORMAL">NORMAL</option>
            <option value="HIGH">HIGH</option>
          </select>
        </div>

        <button type="submit">登録</button>
        <button type="button" onClick={onClose}>閉じる</button>
      </form>
    </div>
  );
};