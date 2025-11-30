import { useEffect, useState } from "react";
import type { Task, TaskInput } from "../../types/task";
import { fetchTasks } from "../../api/taskApi";
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
    const fromEdit = toInputDate(editingTask?.dueDate);
    if (fromEdit !== undefined) return fromEdit;
    return initialDueDate ?? undefined;
  });
  // AIで細分化（新規/編集の両方で選択可能）
  const [aiDecompose, setAiDecompose] = useState<boolean>(false);
  // 子タスクの有無に関係なくチェックボックスを表示するため、状態は不要
  const [atMaxDepth, setAtMaxDepth] = useState<boolean>(false); // ルートからの深さが4以上

  // 編集モードで既存子タスク有無と深さを取得し、条件に応じてAI細分化チェックを非表示
  useEffect(() => {
    const loadChildren = async () => {
      if (isEdit && editingTask) {
        try {
          const all = await fetchTasks();
          const getParentId = (t: Task) => t.parentTaskId as number | undefined;
          const children = all.filter(t => getParentId(t) === editingTask.id);
          console.debug("[TaskCreateForm] edit load children count=", children.length, "editingId=", editingTask.id);
           // 子タスクが存在していてもチェックボックスは表示するため、状態管理は不要
          // 深さ計算（root=1）。editingTask自身の深さを算出。
          let depth = 1;
          let pid = getParentId(editingTask);
          // サイクル防止に上限
          let guard = 0;
          while (pid && guard++ < 128) {
            depth += 1;
            const parent = all.find(t => t.id === pid);
            if (!parent) break;
            pid = getParentId(parent);
          }
          setAtMaxDepth(depth >= 4); // 4以上の深さを設定
        } catch (e) {
          // 失敗時は表示を維持（ログのみ）
          console.warn("子タスク取得に失敗", e);
        }
      }
    };
    loadChildren();
  }, [isEdit, editingTask]);

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
      ai_decompose: aiDecompose,
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

          {!atMaxDepth && (
            <div className="form-group form-check" style={{ marginTop: 8 }}>
              <input
                id="aiDecompose"
                name="aiDecompose"
                type="checkbox"
                className="form-check-input checkbox-small"
                checked={aiDecompose}
                onChange={(e) => setAiDecompose(e.target.checked)}
              />
              <label htmlFor="aiDecompose" className="form-check-label">
                AIでタスクを細分化（小タスクを自動作成）
              </label>
              <div className="ai-hint" style={{ marginTop: 4 }}>
                {isEdit ? "更新後に小タスク生成" : "作成後に小タスク生成"}
              </div>
            </div>
          )}
          {/* 子タスク存在時の再細分化不可メッセージは撤廃（最大深度のみ制限表示） */}
          {atMaxDepth && (
            <div style={{ marginTop: 8, fontSize: 12, color: "#666" }}>
              階層は最大4までです。このタスクは最大階層に達しているため細分化できません。
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