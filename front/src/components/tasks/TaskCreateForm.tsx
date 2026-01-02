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
  parentIdForCreate?: number; // 新規作成時に親IDを指定（子タスク追加）
  hideAiDecompose?: boolean; // 階層制限によりAI細分化チェックを隠す
};

// タスク作成・編集フォームコンポーネント
export const TaskCreateForm = ({
  editingTask,
  onCreated,
  onUpdated,
  onClose,
  initialDueDate,
  parentIdForCreate,
  hideAiDecompose,
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
  const [titleError, setTitleError] = useState<string | null>(null);
  const [description, setDescription] = useState(() => editingTask?.description ?? "");
  const [descError, setDescError] = useState<string | null>(null);
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
    const fromEdit = toInputDate(editingTask?.dueDate);
    if (fromEdit !== undefined) return fromEdit;
    return initialDueDate ?? undefined;
  });
  const [dueError, setDueError] = useState<string | null>(null);
  // 新規作成モードのみ、AI細分化チェックを提供（編集モードでは提供しない）
  const [aiDecomposeRaw, setAiDecomposeRaw] = useState<boolean>(false);
  const aiDecompose = !isEdit && hideAiDecompose ? false : aiDecomposeRaw;


  // フォーム送信ハンドラ
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    // タイトル必須のUIバリデーション
    const trimmedTitle = (title ?? "").trim();
    if (!trimmedTitle) {
      setTitleError("タイトルは必須です");
      return;
    }
    setTitleError(null);
    // AIプレビュー利用時は説明必須（質の担保）
    const trimmedDesc = (description ?? "").trim();
    if (!isEdit && aiDecompose && !trimmedDesc) {
      setDescError("AIプレビューには説明が必要です");
      return;
    }
    setDescError(null);
    // 期限日の手入力バリデーション（任意入力だが、入力がある場合は検証）
    if (dueDate && dueDate !== "") {
      const s = dueDate.trim();
      const m = s.match(/^(\d{4})-(\d{2})-(\d{2})$/);
      if (!m) {
        setDueError("期限日は yyyy-MM-dd 形式で入力してください");
        return;
      }
      const y = Number(m[1]);
      const mo = Number(m[2]);
      const d = Number(m[3]);
      // 月1-12、日1-31の基本チェック＋存在判定
      const isValidBase = y >= 1900 && y <= 9999 && mo >= 1 && mo <= 12 && d >= 1 && d <= 31;
      if (!isValidBase) {
        setDueError("期限日が不正です");
        return;
      }
      const dt = new Date(Date.UTC(y, mo - 1, d));
      // 正規化しても同じ値か（存在確認、例えば 2026-02-30 は無効）
      const same = dt.getUTCFullYear() === y && (dt.getUTCMonth() + 1) === mo && dt.getUTCDate() === d;
      if (!same) {
        setDueError("存在しない日付です");
        return;
      }
    }
    setDueError(null);
    // 入力値
    const input: TaskInput = {
      title: trimmedTitle,
      description: trimmedDesc,
      priority: uiToDbPriority(priority),
      status: uiToDbStatus(status),
      due_date: dueDate && dueDate !== "" ? dueDate : undefined,
      // 新規作成時のみAI細分化フラグを送信
      ...(isEdit ? {} : { ai_decompose: aiDecompose }),
      // 新規作成時の親ID（両フィールド送るがサーバ側はどちらでも受理）
      ...(editingTask ? {} : (parentIdForCreate ? { parent_task_id: parentIdForCreate, parentTaskId: parentIdForCreate } : {})),
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
            {titleError && (
              <div className="form-error" style={{ color: "#c00", marginTop: 4 }}>{titleError}</div>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="description">説明</label>
            <textarea id="description" name="description" value={description} onChange={(e) => setDescription(e.target.value)} />
            {(!isEdit && aiDecompose && descError) && (
              <div className="form-error" style={{ color: "#c00", marginTop: 4 }}>{descError}</div>
            )}
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
            {dueError && (
              <div className="form-error" style={{ color: "#c00", marginTop: 4 }}>{dueError}</div>
            )}
          </div>

          {/* 新規作成時のみAI細分化を提供（編集では非表示）。さらにhideAiDecompose指定時は非表示 */}
          {!isEdit && !hideAiDecompose && (
            <div className="form-group form-check" style={{ marginTop: 8 }}>
              <input
                id="aiDecompose"
                name="aiDecompose"
                type="checkbox"
                className="form-check-input checkbox-small"
                checked={aiDecomposeRaw}
                onChange={(e) => {
                  const checked = e.target.checked;
                  setAiDecomposeRaw(checked);
                  if (!checked) setDescError(null);
                }}
              />
              <label htmlFor="aiDecompose" className="form-check-label">
                AIでタスクを細分化（プレビューで提案を表示）
              </label>
              <div className="ai-hint" style={{ marginTop: 4, color: "#555" }}>
                チェックすると作成後にAIの提案プレビューを表示します。提案の保存は選択式です。AIプレビューを使う場合は説明の入力が必要です。説明が具体的なほど、より良い分解提案が得られます。
              </div>
            </div>
          )}

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