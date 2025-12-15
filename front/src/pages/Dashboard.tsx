// ユーティリティ（モジュールスコープに配置）
// 柔軟な日付文字列をミリ秒に変換（yyyy/MM/dd, yyyy-MM-dd, ISO8601）
const parseDateFlexibleToEpoch = (s?: string | null): number => {
  if (!s) return NaN;
  // yyyy/MM/dd
  const m1 = s.match(/^(\d{4})\/(\d{2})\/(\d{2})$/);
  if (m1) {
    const dt = new Date(Number(m1[1]), Number(m1[2]) - 1, Number(m1[3]));
    return dt.getTime();
  }
  // yyyy-MM-dd
  const m2 = s.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (m2) {
    const dt = new Date(Number(m2[1]), Number(m2[2]) - 1, Number(m2[3]));
    return dt.getTime();
  }
  // ISO 8601 などその他
  const ms = Date.parse(s);
  return Number.isNaN(ms) ? NaN : ms;
};

// NaN を「末尾」に押し出す昇順比較
const compareEpochAsc = (a: number, b: number): number => {
  const aNaN = Number.isNaN(a);
  const bNaN = Number.isNaN(b);
  if (aNaN && bNaN) return 0;
  if (aNaN) return 1; // a 不明 → 後ろへ
  if (bNaN) return -1; // b 不明 → 後ろへ
  return a - b;
};

// 降順（大きいほど前）
const compareEpochDesc = (a: number, b: number): number =>
  compareEpochAsc(b, a);

// コンポーネント本体
import { useEffect, useMemo, useState } from "react";
import { useAuth } from "../context/authContext";
import { useNavigate } from "react-router-dom";
// 型定義、API、コンポーネント
import type { Task, TaskInput } from "../types/task";
import { fetchTasks, deleteTask, updateTask, createTask } from "../api/taskApi";
import { TaskList } from "../components/tasks/TaskList";
import { TaskCalendar } from "../components/tasks/TaskCalendar";
import { TaskLegend } from "../components/tasks/TaskLegend";
import { TaskFilters } from "../components/tasks/TaskFilters";
import { TaskSort } from "../components/tasks/TaskSort";
import { TaskCreateForm } from "../components/tasks/TaskCreateForm";
import { fetchAiQuotaStatus, type AiQuotaStatus } from "../api/aiApi";
// スタイル
import "./Dashboard.css";
import "./DashboardFilters.css";

// ダッシュボードコンポーネント
const Dashboard = () => {
  // 認証コンテキスト、ナビゲーション
  const { logout } = useAuth();
  const navigate = useNavigate();
  // 状態管理
  const [allTasks, setAllTasks] = useState<Task[]>([]);
  const [status, setStatus] = useState("");
  const [priority, setPriority] = useState("");
  const [sort, setSort] = useState("due_date_asc");
  // クイックフィルタ（today/soon/not_done など）
  const [quickFilter, setQuickFilter] = useState<"all" | "today" | "soon" | "not_done">("all");
  // 編集中タスク
  const [editingTask, setEditingTask] = useState<Task | null>(null);
  // モーダル表示
  const [showForm, setShowForm] = useState(false);
  // カレンダーセルから新規作成する際の初期期日
  const [initialDueDateForCreate, setInitialDueDateForCreate] = useState<string | undefined>(undefined);
  // 手動子作成用の親ID
  const [parentIdForCreate, setParentIdForCreate] = useState<number | undefined>(undefined);
  // 表示モード(list | calendar)
  const [view, setView] = useState<'list' | 'calendar'>("list");
  // カレンダー用の月（1日を基準）
  const [calendarMonth, setCalendarMonth] = useState(() => {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1);
  });
  // 子コンポーネントへ更新を通知するためのバージョンカウンタ
  const [dataVersion, setDataVersion] = useState(0);
  // AIクオータ表示用
  const [aiQuota, setAiQuota] = useState<AiQuotaStatus | null>(null);
  const [aiError, setAiError] = useState<string | null>(null);
  const [quotaLoading, setQuotaLoading] = useState(false);
  const [showPlanModal, setShowPlanModal] = useState(false);

  // タスク一覧取得
  useEffect(() => {
    const load = async () => {
      try {
        const tasks = await fetchTasks();
        setAllTasks(tasks);
      } catch (e) {
        console.error(e);
      }
    };
    load();
  }, []);

  // AIクオータ取得
  const reloadQuota = async () => {
    setQuotaLoading(true);
    try {
      const status = await fetchAiQuotaStatus();
      setAiQuota(status);
      setAiError(null);
    } catch (e: unknown) {
      const respStatus = (e as { response?: { status?: number } })?.response?.status;
      if (respStatus === 503) {
        setAiError("AI連携が未設定です。管理者に連絡するか、OPENAI_API_KEY を設定してください。");
      } else {
        setAiError("AI利用状況の取得に失敗しました。");
      }
    } finally {
      setQuotaLoading(false);
    }
  };
  useEffect(() => { void reloadQuota(); }, []);

  // フィルタ＆ソート
  const filteredTasks = useMemo(() => {
    // フィルタ
    const list = allTasks.filter((t) => {
      if (status && t.status !== status) return false;
      if (priority && t.priority !== priority) return false;
      // 追加クイックフィルタ
      if (quickFilter !== "all") {
        const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
        const today = startOfDay(new Date());
        const dueMs = parseDateFlexibleToEpoch(t?.dueDate);
        const due = Number.isNaN(dueMs) ? null : new Date(dueMs);
        const isDone = t.status === "DONE";
        if (quickFilter === "not_done" && isDone) return false;
        if (quickFilter === "today") {
          if (!due) return false;
          if (startOfDay(due).getTime() !== today.getTime()) return false;
        }
        if (quickFilter === "soon") {
          if (!due || isDone) return false;
          const diffMs = startOfDay(due).getTime() - today.getTime();
          const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
          if (diffDays < 0 || diffDays > 3) return false;
        }
      }
      return true;
    });

    // ソート
    list.sort((a, b) => {
      const dueA = parseDateFlexibleToEpoch(a?.dueDate);
      const dueB = parseDateFlexibleToEpoch(b?.dueDate);
      const createdA = parseDateFlexibleToEpoch(a?.createdAt);
      const createdB = parseDateFlexibleToEpoch(b?.createdAt);

      switch (sort) {
        case "due_date_asc":
          // 期限日が近い順（早い期日ほど前）。期限なしは末尾。
          return compareEpochAsc(dueA, dueB);

        case "due_date_desc":
          // 期限日が遠い順（遅い期日ほど前）。期限なしは末尾。
          return compareEpochDesc(dueA, dueB);

        case "created_desc":
          // 作成日の新しい順。期限の有無は関係させない。
          return compareEpochDesc(createdA, createdB);

        case "created_asc":
          // 作成日の古い順。期限の有無は関係させない。
          return compareEpochAsc(createdA, createdB);

        default:
          return 0;
      }
    });

    return list;
  }, [status, priority, sort, allTasks, quickFilter]);

  // 統計（今日/完了/未完了/期日が近い）
  const { todayCount, doneCount, notDoneCount, soonDueCount } = useMemo(() => {
    const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
    const today = startOfDay(new Date());
    const withinDays = (base: Date, target: Date, days: number) => {
      const diffMs = startOfDay(target).getTime() - base.getTime();
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
      return diffDays >= 0 && diffDays <= days; // 今日含む
    };

    let tCount = 0; // 今日が期限
    let dCount = 0; // 完了
    let ndCount = 0; // 未完了
    let sCount = 0; // 期限が近い（3日以内, 未完了のみ）

    for (const t of allTasks) {
      const dueMs = parseDateFlexibleToEpoch(t.dueDate);
      const due = Number.isNaN(dueMs) ? null : new Date(dueMs);
      const isDone = t.status === "DONE";

      if (isDone) dCount++; else ndCount++;
      if (due) {
        if (startOfDay(due).getTime() === today.getTime()) tCount++;
        if (!isDone && withinDays(today, due, 3)) sCount++;
      }
    }

    return { todayCount: tCount, doneCount: dCount, notDoneCount: ndCount, soonDueCount: sCount };
  }, [allTasks]);

  // 編集開始
  const handleEdit = (task: Task) => {
    setEditingTask(task);
    setShowForm(true);
  } 

  // タスク更新ハンドラ
  const handleUpdate = async (id: number, input: TaskInput) => {
    try {
  await updateTask(id, input);
  const tasks = await fetchTasks();
  setAllTasks(tasks);
  setDataVersion(v => v + 1);
      setShowForm(false);
      setEditingTask(null);
    } catch (error) {
      console.error("更新エラー:", error);
    }
  };

  // タスク作成ハンドラ
  const handleCreated = async (input: TaskInput) => {
    try {
  await createTask(input);
  const tasks = await fetchTasks();
  setAllTasks(tasks);
  setDataVersion(v => v + 1);
      setShowForm(false);
      setEditingTask(null);
    } catch (error) {
      console.error("作成エラー:", error);
    }
  };

  // タスク削除ハンドラ
  const handleDelete = async (taskId: number) => {
    try {
  await deleteTask(taskId);
  const tasks = await fetchTasks();
  setAllTasks(tasks);
  setDataVersion(v => v + 1);
    } catch (e) {
      console.error("削除エラー:", e);
    }
  };

  return (
    <div className="dashboard-container">
      <div className="dashboard-card">
        <div className="dashboard-header header-row">
          <div className="dashboard-header-top">
            <div className="dashboard-title-inline">タスク一覧</div>
            <div className="dashboard-header-actions">
              <button
                className="btn btn-primary me-2"
                onClick={() => {
                  setEditingTask(null);
                  setShowForm(true);
                }}
              >新規</button>
              <button
                className="btn btn-danger"
                onClick={async () => {
                  await logout();
                  navigate("/login");
                }}
              >ログアウト</button>
            </div>
          </div>
          {/* タイトル直下の中央揃えエラー表示 */}
          {aiError && (
            <div style={{ textAlign: "center", color: "#b00020", padding: "4px 0" }}>
              <span style={{ fontSize: "0.9rem" }}>{aiError}</span>
            </div>
          )}
          <div className="dashboard-header-sub">
            <div className="dashboard-view-toggle-right">
              <button
                className="btn btn-primary"
                onClick={() => setView(view === 'list' ? 'calendar' : 'list')}
                style={{ fontSize: "0.9rem", lineHeight: 1.0, paddingTop: 2, paddingBottom: 2 }}
              >
                {view === 'list' ? (
                  <>
                    <div>カレンダー</div>
                    <div>表示</div>
                  </>
                ) : (
                  <>
                    <div>リスト</div>
                    <div>表示</div>
                  </>
                )}
              </button>
            </div>
            {/* AIクオータ表示（右寄せの隣に薄いテキスト） */}
            <div style={{ marginLeft: "auto", paddingLeft: 12, color: "#666", maxWidth: 360 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                {aiError ? (
                  <>
                    {/* 右側ではテキストは省略し、操作系のみ */}
                    <button className="btn btn-sm btn-outline-secondary" onClick={reloadQuota} disabled={quotaLoading}>
                      {quotaLoading ? "再試行中..." : "再試行"}
                    </button>
                    <a className="link-secondary" href="/docs/ai-setup" target="_blank" rel="noreferrer">設定手順</a>
                  </>
                ) : aiQuota ? (
                  aiQuota.unlimited ? (
                    <span>AI利用状況: 無制限（{aiQuota.planName}）</span>
                  ) : (
                    <span>AI利用状況: 残り {aiQuota.remaining} 回（{aiQuota.planName}）</span>
                  )
                ) : (
                  <span>AI利用状況: 取得中...</span>
                )}
                <button className="btn btn-sm btn-outline-primary" onClick={() => setShowPlanModal(true)}>
                  プラン変更
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* ステータスサマリ */}
        <div className="dashboard-stats">
          <div
            className={`stat-item ${quickFilter === "today" ? "active" : ""}`}
            role="button"
            tabIndex={0}
            onClick={() => setQuickFilter(quickFilter === "today" ? "all" : "today")}
          >
            <div className="stat-label">今日のタスク</div>
            <div className="stat-value">{todayCount}</div>
          </div>
          <div
            className={`stat-item ${status === "DONE" ? "active" : ""}`}
            role="button"
            tabIndex={0}
            onClick={() => {
              // トグル: DONE -> 解除
              setQuickFilter("all");
              setStatus(status === "DONE" ? "" : "DONE");
            }}
          >
            <div className="stat-label">完了</div>
            <div className="stat-value">{doneCount}</div>
          </div>
          <div
            className={`stat-item ${quickFilter === "not_done" && !status ? "active" : ""}`}
            role="button"
            tabIndex={0}
            onClick={() => {
              // 未完了はstatusフィルタは解除してクイックフィルタで除外
              setStatus("");
              setQuickFilter(quickFilter === "not_done" ? "all" : "not_done");
            }}
          >
            <div className="stat-label">未完了</div>
            <div className="stat-value">{notDoneCount}</div>
          </div>
          <div
            className={`stat-item ${quickFilter === "soon" ? "active" : ""}`}
            role="button"
            tabIndex={0}
            onClick={() => {
              setStatus("");
              setQuickFilter(quickFilter === "soon" ? "all" : "soon");
            }}
          >
            <div className="stat-label">期限が近い(3日)</div>
            <div className="stat-value">{soonDueCount}</div>
          </div>
        </div>

        {/* フィルタ */}
        <div className="dashboard-filters-card">
          <div className="dashboard-filters-row">
            <div style={{ flex: 1 }}>
              <div className="dashboard-filters-label">ステータス</div>
              <TaskFilters
                status={status}
                priority={priority}
                onStatusChange={setStatus}
                onPriorityChange={setPriority}
                showStatus={true}
                showPriority={false}
              />
            </div>

            <div style={{ flex: 1 }}>
              <div className="dashboard-filters-label">優先度</div>
              <TaskFilters
                status={status}
                priority={priority}
                onStatusChange={setStatus}
                onPriorityChange={setPriority}
                showStatus={false}
                showPriority={true}
              />
            </div>

            <div style={{ flex: 1 }}>
              <div className="dashboard-filters-label">期限日が近い順</div>
              <TaskSort sort={sort} onSortChange={setSort} />
            </div>
          </div>
        </div>

        {view === 'list' && (
          <TaskList
            tasks={filteredTasks}
            onDelete={handleDelete}
            onEdit={handleEdit}
            onCreateChild={(pid) => {
              setEditingTask(null);
              setParentIdForCreate(pid);
              setShowForm(true);
            }}
            key={`list-${dataVersion}`}
          />
        )}
        {view === 'calendar' && (
          <>
            <TaskCalendar
              tasks={filteredTasks}
              month={calendarMonth}
              onPrev={() => setCalendarMonth(m => new Date(m.getFullYear(), m.getMonth() - 1, 1))}
              onNext={() => setCalendarMonth(m => new Date(m.getFullYear(), m.getMonth() + 1, 1))}
              onEdit={(task) => handleEdit(task)}
                onCreate={(dateISO) => {
                  setEditingTask(null);
                  setInitialDueDateForCreate(dateISO);
                  setShowForm(true);
                }}
            />
            <TaskLegend />
          </>
        )}

        {/* 新規 or 編集モーダル */}
        {showForm && (
          <TaskCreateForm
            editingTask={editingTask}
            onCreated={editingTask ? undefined : handleCreated}
            onUpdated={editingTask ? handleUpdate : undefined}
            initialDueDate={initialDueDateForCreate}
            parentIdForCreate={parentIdForCreate}
            onClose={() => {
              setShowForm(false);
              setEditingTask(null);
              setInitialDueDateForCreate(undefined);
              setParentIdForCreate(undefined);
            }}
          />
        )}
      </div>

      {/* プラン変更モーダル（スケルトン） */}
      {showPlanModal && (
        <div className="modal d-block" tabIndex={-1}>
          <div className="modal-dialog">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">プランを選択</h5>
                <button type="button" className="btn-close" onClick={() => setShowPlanModal(false)}></button>
              </div>
              <div className="modal-body">
                <div className="form-check">
                  <input className="form-check-input" type="radio" name="planRadio" id="planFree" defaultChecked />
                  <label className="form-check-label" htmlFor="planFree">Free（AI不可）</label>
                </div>
                <div className="form-check">
                  <input className="form-check-input" type="radio" name="planRadio" id="planStarter" />
                  <label className="form-check-label" htmlFor="planStarter">Starter（月間少量）</label>
                </div>
                <div className="form-check">
                  <input className="form-check-input" type="radio" name="planRadio" id="planPro" />
                  <label className="form-check-label" htmlFor="planPro">Pro（月間多め）</label>
                </div>
                <div className="form-check">
                  <input className="form-check-input" type="radio" name="planRadio" id="planUnlimited" />
                  <label className="form-check-label" htmlFor="planUnlimited">Unlimited（無制限）</label>
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setShowPlanModal(false)}>閉じる</button>
                <button className="btn btn-primary" onClick={() => { /* TODO: backend POST /api/subscriptions/change */ setShowPlanModal(false); }}>適用</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Dashboard;