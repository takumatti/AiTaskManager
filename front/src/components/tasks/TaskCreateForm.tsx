import { useEffect, useState } from "react";
import type { Task, TaskInput } from "../../types/task";
import "./TaskCreateForm.css";

// Props タイプ定義
type Props = {
  editingTask?: Task | null;
  onCreated?: (data: TaskInput) => void;
  onUpdated?: (id: number, data: TaskInput) => void;
  onClose: () => void;
  initialDueDate?: string; // yyyy-MM-dd 形式（新規作成時の初期期限日）
};

// タスク作成・編集フォームコンポーネント
export const TaskCreateForm = ({
  editingTask,
  onCreated,
  onUpdated,
  onClose,
  initialDueDate,
}: Props) => {
  const isEdit = !!editingTask;
  // モーダル表示中は背景スクロールを禁止
  useEffect(() => {
    const prevOverflow = document.body.style.overflow;
    const prevPaddingRight = document.body.style.paddingRight;

    // スクロールバー幅を計算してレイアウトシフトを防止
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
    if (scrollbarWidth > 0) {
      document.body.style.paddingRight = `${scrollbarWidth}px`;
    }
    document.body.classList.add("no-scroll");
    document.body.style.overflow = "hidden";
    return () => {
      document.body.classList.remove("no-scroll");
      document.body.style.overflow = prevOverflow;
      document.body.style.paddingRight = prevPaddingRight;
    };
  }, []);

  // 初期値
  const [title, setTitle] = useState(() => editingTask?.title ?? "");
  const [description, setDescription] = useState(() => editingTask?.description ?? "");
  // DB値→UI値変換
  const dbToUiStatus = (status?: string) => {
    switch (status) {
      case "TODO": return "未着手";
      case "DOING": return "進行中";
      case "DONE": return "完了";
      default: return "未着手";
    }
  };
  const dbToUiPriority = (priority?: string) => {
    switch (priority) {
      case "HIGH": return "高";
      case "NORMAL": return "中";
      case "LOW": return "低";
      default: return "中";
    }
  };
  const uiToDbStatus = (status: string) => {
    switch (status) {
      case "未着手": return "TODO";
      case "進行中": return "DOING";
      case "完了": return "DONE";
      default: return "TODO";
    }
  };
  const uiToDbPriority = (priority: string) => {
    switch (priority) {
      case "高": return "HIGH";
      case "中": return "NORMAL";
      case "低": return "LOW";
      default: return "NORMAL";
    }
  };

  // selectのvalueはUI値（日本語）
  const [priority, setPriority] = useState(() => dbToUiPriority(editingTask?.priority));
  const [status, setStatus] = useState(() => dbToUiStatus(editingTask?.status));
  // yyyy/MM/dd→yyyy-MM-dd変換（input type=date用）
  const toInputDate = (d?: string) => {
    if (!d) return undefined;
    // yyyy/MM/dd → yyyy-MM-dd
    const m = d.match(/(\d{4})\/(\d{2})\/(\d{2})/);
    if (m) return `${m[1]}-${m[2]}-${m[3]}`;
    // 既にyyyy-MM-ddならそのまま
    if (/^\d{4}-\d{2}-\d{2}$/.test(d)) return d;
    return d;
  };
  const [dueDate, setDueDate] = useState<string | undefined>(() => {
    // 編集時は既存値を優先、非編集（新規）時はinitialDueDateを採用
    const fromEdit = toInputDate(editingTask?.due_date);
    if (fromEdit !== undefined) return fromEdit;
    return initialDueDate ?? undefined;
  });

  // フォーム送信ハンドラ
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    // 入力値

    const input: TaskInput = {
      title,
      description: description ?? "",
      priority: uiToDbPriority(priority),
      status: uiToDbStatus(status),
      due_date: dueDate && dueDate !== "" ? dueDate : undefined,
    };

    // 作成 or 更新のコールバック呼び出し
    if (isEdit && editingTask && onUpdated) {
      onUpdated(editingTask.id, input);
    } else if (onCreated) {
      onCreated(input);
    }

    onClose();
  };

  return (
    <div className="modal-overlay">
      <div className="task-modal">
        <h3>{isEdit ? "タスク編集" : "新規タスク"}</h3>

        <form onSubmit={handleSubmit} className="task-form-vertical">
          <div className="form-group">
            <label htmlFor="title">タイトル</label>
            <input id="title" name="title" value={title} onChange={(e) => setTitle(e.target.value)} />
          </div>

          <div className="form-group">
            <label htmlFor="description">説明</label>
            <textarea id="description" name="description" value={description} onChange={(e) => setDescription(e.target.value)} />
          </div>

          <div className="form-group">
            <label htmlFor="priority">優先度</label>
            <select
              id="priority"
              name="priority"
              value={priority}
              onChange={(e) => setPriority(e.target.value)}
            >
              <option value="高">高</option>
              <option value="中">中</option>
              <option value="低">低</option>
            </select>
          </div>

          <div className="form-group">
            <label htmlFor="status">ステータス</label>
            <select id="status" name="status" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="未着手">未着手</option>
              <option value="進行中">進行中</option>
              <option value="完了">完了</option>
            </select>
          </div>

          <div className="form-group">
            <label htmlFor="dueDate">期限日</label>
            <input
              id="dueDate"
              name="dueDate"
              type="date"
              value={dueDate ?? ""}
              onChange={(e) => setDueDate(e.target.value || undefined)}
            />
          </div>

          <div className="modal-actions">
            <button type="submit" className="btn btn-primary">
              {isEdit ? "更新" : "作成"}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={onClose}
            >
              閉じる
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};