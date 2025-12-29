import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/authContext";
import type { Task, TaskInput } from "../types/task";
import { fetchTasks, deleteTask, updateTask, createTask } from "../api/taskApi";
import { TaskList } from "../components/tasks/TaskList";
import { TaskCalendar } from "../components/tasks/TaskCalendar";
import { TaskLegend } from "../components/tasks/TaskLegend";
import { TaskFilters } from "../components/tasks/TaskFilters";
import { TaskSort } from "../components/tasks/TaskSort";
import { TaskCreateForm } from "../components/tasks/TaskCreateForm";
import {
  fetchAiQuotaStatus,
  type AiQuotaStatus,
  fetchPlans,
  type SubscriptionPlan,
  createCreditCheckout,
} from "../api/aiApi";
import { fetchCreditPacks, type CreditPack } from "../api/creditPackApi";
import apiClient from "../api/apiClient";
import "./Dashboard.css";
import "./DashboardFilters.css";

// ユーティリティ
const parseDateFlexibleToEpoch = (s?: string | null): number => {
  if (!s) return NaN;
  const m1 = s.match(/^(\d{4})\/(\d{2})\/(\d{2})$/);
  if (m1) return new Date(Number(m1[1]), Number(m1[2]) - 1, Number(m1[3])).getTime();
  const m2 = s.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (m2) return new Date(Number(m2[1]), Number(m2[2]) - 1, Number(m2[3])).getTime();
  const ms = Date.parse(s);
  return Number.isNaN(ms) ? NaN : ms;
};
const compareEpochAsc = (a: number, b: number) => {
  const aNaN = Number.isNaN(a);
  const bNaN = Number.isNaN(b);
  if (aNaN && bNaN) return 0;
  if (aNaN) return 1;
  if (bNaN) return -1;
  return a - b;
};
const compareEpochDesc = (a: number, b: number) => compareEpochAsc(b, a);

export default function Dashboard() {
  const { logout, auth } = useAuth();
  const isAdmin = Array.isArray(auth?.roles) && auth!.roles!.includes("ADMIN");
  const navigate = useNavigate();

  // タスク関連
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
  const [view, setView] = useState<"list" | "calendar">("list");
  // カレンダー用の月（1日を基準）
  const [calendarMonth, setCalendarMonth] = useState(() => {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1);
  });
  // 子コンポーネントへ更新を通知するためのバージョンカウンタ
  const [dataVersion, setDataVersion] = useState(0);
  // AIクオータ・プラン
  const [aiQuota, setAiQuota] = useState<AiQuotaStatus | null>(null);
  const [aiError, setAiError] = useState<string | null>(null);
  const [quotaLoading, setQuotaLoading] = useState(false);
  const [showPlanModal, setShowPlanModal] = useState(false);
  const [plans, setPlans] = useState<SubscriptionPlan[] | null>(null);
  const [selectedPlanId, setSelectedPlanId] = useState<number | null>(null);
  const [planLoading, setPlanLoading] = useState(false);
  const [planError, setPlanError] = useState<string | null>(null);
  const [planMessage, setPlanMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [showDocsModal, setShowDocsModal] = useState(false);
  const [creditBuying, setCreditBuying] = useState(false);
  const [showLimitModal, setShowLimitModal] = useState(false);
  const [creditPacks, setCreditPacks] = useState<CreditPack[]>([]);
  const [creditPacksLoading, setCreditPacksLoading] = useState(false);
  const [creditPacksError, setCreditPacksError] = useState<string | null>(null);

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
    void load();
  }, []);

  // 決済成功メッセージ
  useEffect(() => {
    const flag = localStorage.getItem("purchaseSuccess");
    if (flag === "true") {
      localStorage.removeItem("purchaseSuccess");
      setPlanMessage({ type: "success", text: "購入が完了しました。プランが適用されました。" });
      const t = setTimeout(() => setPlanMessage(null), 5000);
      return () => clearTimeout(t);
    }
  }, []);

  // 成功メッセージの自動消去（5秒）。他のトリガーでsuccessがセットされた場合にも対応。
  useEffect(() => {
    if (planMessage?.type === "success") {
      const timer = setTimeout(() => setPlanMessage(null), 5000);
      return () => clearTimeout(timer);
    }
  }, [planMessage]);
  useEffect(() => { void reloadQuota(); }, []);

  // クレジットパック一覧取得
  useEffect(() => {
    const loadPacks = async () => {
      try {
        setCreditPacksLoading(true);
        const packs = await fetchCreditPacks();
        setCreditPacks(packs);
        setCreditPacksError(null);
      } catch (e) {
        console.error(e);
        setCreditPacksError("クレジットパックの取得に失敗しました");
      } finally {
        setCreditPacksLoading(false);
      }
    };
    void loadPacks();
  }, []);

  // プラン一覧取得（モーダル表示時）
  useEffect(() => {
    const loadPlans = async () => {
      if (!showPlanModal) return;
      try {
        setPlanLoading(true);
        const resp = await fetchPlans();
        setPlans(resp);
        const currentId = aiQuota?.displayPlanId ?? null;
        setSelectedPlanId(currentId);
        setPlanError(null);
      } catch (e: unknown) {
        const msg = (e as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message
          ?? (e as { message?: string }).message
          ?? "プラン一覧の取得に失敗しました";
        setPlanError(msg);
      } finally {
        setPlanLoading(false);
      }
    };
    void loadPlans();
  }, [showPlanModal, aiQuota?.planId, aiQuota?.displayPlanId]);

  // フィルタ適用
  const filteredTasks = useMemo(() => {
    // フィルタリング
    const list = allTasks.filter((t) => {
      const matchesStatus = status ? t.status === status : true;
      const matchesPriority = priority ? (t.priority || "") === priority : true;
      if (!matchesStatus || !matchesPriority) return false;

      const isDone = t.status === "DONE";
      const due = t.dueDate ? new Date(t.dueDate) : undefined;
      const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
      const today = startOfDay(new Date());

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
      if (quickFilter === "not_done") {
        if (isDone) return false;
      }
      return true;
    });

    // ソート適用
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

  // 統計
  const { todayCount, doneCount, notDoneCount, soonDueCount } = useMemo(() => {
    const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
    const today = startOfDay(new Date());
    const withinDays = (base: Date, target: Date, days: number) => {
      const diffMs = startOfDay(target).getTime() - base.getTime();
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
      return diffDays >= 0 && diffDays <= days;
    };

    let tCount = 0; // 今日が期限
    let dCount = 0; // 完了
    let ndCount = 0; // 未完了
    let sCount = 0; // 期限が近い（3日以内, 未完了のみ）

    for (const t of allTasks) {
      const due = t.dueDate ? new Date(t.dueDate) : undefined;
      const isDone = t.status === "DONE";
      if (due && startOfDay(due).getTime() === today.getTime()) tCount++;
      if (isDone) dCount++;
      if (!isDone) ndCount++;
      if (!isDone && due && withinDays(today, due, 3)) sCount++;
    }

    return { todayCount: tCount, doneCount: dCount, notDoneCount: ndCount, soonDueCount: sCount };
  }, [allTasks]);
 
  // 編集開始ハンドラ
  const handleEdit = (task: Task) => {
    setEditingTask(task);
    setShowForm(true);
  };
  // タスク更新ハンドラ
  const handleUpdate = async (taskId: number, input: TaskInput) => {
    try {
      await updateTask(taskId, input);
      const tasks = await fetchTasks();
      setAllTasks(tasks);
      setShowForm(false);
      setEditingTask(null);
      setDataVersion((v) => v + 1);
    } catch (e) {
      console.error("更新エラー:", e);
    }
  };
  // タスク作成ハンドラ
  const handleCreated = async (input: TaskInput) => {
    try {
      await createTask(input);
      const tasks = await fetchTasks();
      setAllTasks(tasks);
      setDataVersion((v) => v + 1);
      setShowForm(false);
      setEditingTask(null);
    } catch (error) {
      console.error("作成エラー:", error);
    }
  };
  // タスク削除ハンドラ
  const handleDelete = async (taskId: number) => {
    try {
      console.debug("[Dashboard] delete requested", taskId);
      setDeleteError(null);
      await deleteTask(taskId);
      const tasks = await fetchTasks();
      setAllTasks(tasks);
      setDataVersion((v) => v + 1);
    } catch (e) {
      console.error("削除エラー:", e);
      const message = (e as Error)?.message || "削除に失敗しました";
      setDeleteError(message);
    }
  };

  return (
    <div className="dashboard-container">
      <div className="dashboard-card">
        <div className="dashboard-header header-row">
          <div className="dashboard-header-top" style={{ position: "relative" }}>
            {/* 設定手順ボタン（管理者のみ） */}
            {isAdmin && (
              <button
                className="btn btn-sm btn-outline-secondary"
                onClick={() => setShowDocsModal(true)}
                title="設定手順"
                style={{ position: "absolute", left: 0, top: 0 }}
              >設定手順</button>
            )}
            <div className="dashboard-title-inline">タスク一覧</div>
            <div className="dashboard-header-actions">
              <button
                className="btn btn-primary me-2"
                onClick={() => { setEditingTask(null); setShowForm(true); }}
              >新規</button>
              <button
                className="btn btn-danger"
                onClick={async () => { await logout(); navigate("/login"); }}
              >ログアウト</button>
            </div>
          </div>

          {/* タイトル直下のエラー表示 */}
          {aiError && (
            <div className="header-inline-error"><span className="header-inline-error-text">{aiError}</span></div>
          )}
          {deleteError && (
            <div className="header-inline-error"><span className="header-inline-error-text">{deleteError}</span></div>
          )}
          {planMessage && (
            <div className="plan-message" style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <span className={`plan-message-chip ${planMessage.type === 'success' ? 'success' : 'error'}`}>{planMessage.text}</span>
              <button type="button" aria-label="閉じる" className="btn btn-sm btn-outline-secondary" onClick={() => setPlanMessage(null)}>×</button>
            </div>
          )}

            <div className="dashboard-header-sub" style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              {/* 左端：表示切替ボタン */}
              <div className="dashboard-view-toggle-left" style={{ flex: '0 0 auto' }}>
              <button
                className="btn btn-primary view-toggle-btn"
                style={{ whiteSpace: 'nowrap', width: 120, minHeight: 36, textAlign: 'center' }}
                onClick={() => setView(view === "list" ? "calendar" : "list")}
              >
                {view === "list" ? (
                  (<><div>カレンダー</div><div>表示</div></>)
                ) : (
                  (<><div>リスト</div><div>表示</div></>)
                )}
              </button>
            </div>

              {/* 中央：AI利用制限＋リセット情報（中央寄せ） */}
              <div className="quota-info" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, maxWidth: '100%', flex: '1 1 auto' }}>
                <div style={{ width: '100%', textAlign: 'center' }}>
                {aiError ? (
                  <button className="btn btn-sm btn-outline-secondary" onClick={reloadQuota} disabled={quotaLoading}>
                    {quotaLoading ? "再試行中..." : "再試行"}
                  </button>
                ) : (
                  <div style={{ whiteSpace: 'normal', wordBreak: 'break-word' }}>
                    {aiQuota ? (
                      aiQuota.unlimited ? (
                        <>
                            <div>
                              <span className="fw-bold">AI利用制限</span>
                              <span className="ms-2">無制限</span>
                              <span className="ms-2 text-muted">（{aiQuota.displayPlanName ?? aiQuota.planName}）</span>
                            </div>
                            {aiQuota.resetDate && (
                              <div>
                                <span className="text-muted">リセット:</span> <span className="fw-semibold">{aiQuota.resetDate}</span>
                                {typeof aiQuota.daysUntilReset === 'number' && (
                                  <span className="ms-1 text-muted">（あと{aiQuota.daysUntilReset}日）</span>
                                )}
                              </div>
                            )}
                        </>
                      ) : (
                        <>
                            <div>
                              <span className="fw-bold">AI利用制限</span>
                              <span className="ms-2">残り <span className="fw-semibold">{aiQuota.remaining ?? 0}</span> 回</span>
                              <span className="ms-2 text-muted">（{aiQuota.displayPlanName ?? aiQuota.planName}）</span>
                            </div>
                            {aiQuota.resetDate && (
                              <div>
                                <span className="text-muted">リセット:</span> <span className="fw-semibold">{aiQuota.resetDate}</span>
                                {typeof aiQuota.daysUntilReset === 'number' && (
                                  <span className="ms-1 text-muted">（あと{aiQuota.daysUntilReset}日）</span>
                                )}
                              </div>
                            )}
                        </>
                      )
                    ) : (
                      <>
                        <span className="fw-bold">AI利用制限</span>
                        <span className="ms-2">残り 0 回</span>
                        <span className="ms-2 text-muted">（取得中）</span>
                      </>
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* 右端：AI利用制限を変更ボタン */}
            <div className="dashboard-limit-change-right" style={{ flex: '0 0 auto' }}>
              <button className="btn btn-sm btn-outline-primary" style={{ whiteSpace: 'nowrap' }} onClick={() => setShowLimitModal(true)}>
                AI利用制限を変更
              </button>
            </div>
          </div>
        </div>

        {/* ステータスサマリ */}
        <div className="dashboard-stats">
          <div className={`stat-item ${quickFilter === "today" ? "active" : ""}`} role="button" tabIndex={0}
               onClick={() => setQuickFilter(quickFilter === "today" ? "all" : "today")}>
            <div className="stat-label">今日のタスク</div>
            <div className="stat-value">{todayCount}</div>
          </div>
          <div className={`stat-item ${status === "DONE" ? "active" : ""}`} role="button" tabIndex={0}
               onClick={() => { setQuickFilter("all"); setStatus(status === "DONE" ? "" : "DONE"); }}>
            <div className="stat-label">完了</div>
            <div className="stat-value">{doneCount}</div>
          </div>
          <div className={`stat-item ${quickFilter === "not_done" && !status ? "active" : ""}`} role="button" tabIndex={0}
               onClick={() => { setStatus(""); setQuickFilter(quickFilter === "not_done" ? "all" : "not_done"); }}>
            <div className="stat-label">未完了</div>
            <div className="stat-value">{notDoneCount}</div>
          </div>
          <div className={`stat-item ${quickFilter === "soon" ? "active" : ""}`} role="button" tabIndex={0}
               onClick={() => { setStatus(""); setQuickFilter(quickFilter === "soon" ? "all" : "soon"); }}>
            <div className="stat-label">期限が近い(3日)</div>
            <div className="stat-value">{soonDueCount}</div>
          </div>
        </div>

        {/* フィルタ */}
        <div className="dashboard-filters-card">
          <div className="dashboard-filters-row">
            <div style={{ flex: 1 }}>
              <div className="dashboard-filters-label">ステータス</div>
              <TaskFilters status={status} priority={priority} onStatusChange={setStatus} onPriorityChange={setPriority} />
            </div>
            <div style={{ width: 240 }}>
              <div className="dashboard-filters-label">並び替え</div>
              <TaskSort sort={sort} onSortChange={setSort} />
            </div>
          </div>
        </div>

        {/* リスト or カレンダー */}
        {view === "list" ? (
          <>
            <TaskList
              key={`list-${dataVersion}`}
              tasks={filteredTasks}
              onEdit={handleEdit}
              onDelete={handleDelete}
            />
            <TaskLegend />
          </>
        ) : (
          <>
            <TaskCalendar
              key={`cal-${dataVersion}`}
              tasks={filteredTasks}
              month={calendarMonth}
              onPrev={() => setCalendarMonth((m) => new Date(m.getFullYear(), m.getMonth() - 1, 1))}
              onNext={() => setCalendarMonth((m) => new Date(m.getFullYear(), m.getMonth() + 1, 1))}
              onEdit={(task) => handleEdit(task)}
              onCreate={(dateISO) => { setEditingTask(null); setInitialDueDateForCreate(dateISO); setShowForm(true); }}
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
            onClose={() => { setShowForm(false); setEditingTask(null); setInitialDueDateForCreate(undefined); setParentIdForCreate(undefined); }}
          />
        )}
      </div>

      {/* プラン変更モーダル */}
      {showPlanModal && (
        <div className="modal d-block" tabIndex={-1}>
          <div className="modal-dialog modal-sm" style={{ maxWidth: "420px" }}>
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">プランを選択</h5>
                <button type="button" className="btn-close" onClick={() => { setShowPlanModal(false); setSelectedPlanId(null); setPlanError(null); }}></button>
              </div>
              <div className="modal-body">
                {planLoading && (<div>読み込み中...</div>)}
                {planError && (<div className="alert alert-warning py-2 mb-2">{planError}</div>)}
                <div>
                  {/* 既存のプラン一覧を簡易表示（選択可能） */}
                  <div className="vstack gap-2">
                    {(plans || []).map((p) => (
                      <label key={p.id} className="d-flex align-items-center gap-2">
                        <input type="radio" name="plan" checked={selectedPlanId === p.id} onChange={() => setSelectedPlanId(p.id)} />
                        <span>
                          {p.description ? (
                            <>
                              {p.name}（{p.description}）
                            </>
                          ) : (
                            p.name
                          )}
                        </span>
                      </label>
                    ))}
                  </div>
                </div>
                <button
                  className="btn btn-success mt-3"
                  disabled={!selectedPlanId}
                  onClick={async () => {
                    if (!selectedPlanId) return;
                    try {
                      // 同一プラン選択時はエラーをモーダル内に表示し、処理を中断
                      const currentPlanId = aiQuota?.displayPlanId ?? null; // ユーザーに表示されている現在のプラン（即時Free反映などを考慮）
                      if (currentPlanId != null && selectedPlanId === currentPlanId) {
                        setPlanError("現在のプランと同じです。回数追加（買い切り）をご利用いただくか、今よりも上位のプランを購入してください。");
                        return;
                      }
                      if (selectedPlanId === 1) {
                        // 無料プランはStripe遷移せず、即時更新
                        await apiClient.post(`/api/billing/change-to-free`, null, { params: { planId: 1 } });
                        setPlanMessage({ type: "success", text: "無料プランに変更しました。" });
                        setShowPlanModal(false);
                        void reloadQuota();
                        return;
                      }
                      // 有料プランはStripe Checkoutへ（未消化の契約枠がある場合は確認ダイアログ）
                      // 現状の型情報では契約枠のみの未消化数を直接取得できないため、近似として remaining を使用します（bonusは含まれる可能性あり）。
                      const unconsumedApprox = Math.max(0, aiQuota?.remaining ?? 0);
                      if (!aiQuota?.unlimited && unconsumedApprox > 0) {
                        const proceed = window.confirm(
                          `今月のアクティブ契約の回数をまだ ${unconsumedApprox} 回分使い切っていない可能性があります。\n有料プランを再購入しますか？\n（注：ボーナス回数は確認対象外です）`
                        );
                        if (!proceed) {
                          return;
                        }
                      }
                      const resp = await apiClient.post(`/api/billing/checkout-session`, null, { params: { planId: selectedPlanId } });
                      const url = resp?.data?.sessionUrl;
                      if (!url) throw new Error("sessionUrl missing");
                      window.location.href = url;
                    } catch (e) {
                      // サーバーからのエラー内容を抽出し、モーダル内に表示
                      const err = e as { response?: { data?: { message?: string } } ; message?: string };
                      const code = err?.response?.data?.message || err?.message || "unknown-error";
                      let msg = "Checkoutの開始に失敗しました";
                      if (code === "downgrade-not-allowed") {
                        msg = "ダウングレードはできません。回数追加（買い切り）をご利用いただくか、今よりも上位のプランを購入してください。";
                      } else if (code === "only-free-allowed") {
                        msg = "無料プラン以外はこの操作では変更できません。";
                      } else if (typeof code === 'string' && code.trim() && code !== 'unknown-error') {
                        // サーバーのmessageがあればそのまま表示
                        msg = code;
                      }
                      console.error("Checkout開始に失敗しました:", code, e);
                      setPlanError(msg);
                    }
                  }}
                >購入画面へ</button>
                <div className="text-muted mt-2" style={{ fontSize: '0.85rem' }}>
                  ※ 本プランの購入はサブスクリプションではなく、1か月毎の買い切り（都度購入）です。
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 設定手順選択モーダル */}
      {showDocsModal && (
        <div className="modal d-block" tabIndex={-1}>
          <div className="modal-dialog">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">設定手順を選択</h5>
                <button type="button" className="btn-close" onClick={() => setShowDocsModal(false)}></button>
              </div>
              <div className="modal-body">
                <div className="vstack gap-3">
                  <div className="p-2 border rounded">
                    <div className="fw-bold">AI連携の設定</div>
                    <div className="text-muted" style={{ fontSize: "0.9rem" }}>OpenAI APIキーの設定方法と動作確認手順</div>
                    <div className="mt-2">
                      <a className="btn btn-outline-primary" href="/docs/ai-setup" target="_blank" rel="noreferrer">AI設定手順を開く</a>
                    </div>
                  </div>
                  <div className="p-2 border rounded">
                    <div className="fw-bold">Stripe導入手順</div>
                    <div className="text-muted" style={{ fontSize: "0.9rem" }}>Checkout と Webhook の設定、テストから本番まで</div>
                    <div className="mt-2">
                      <a className="btn btn-outline-success" href="/docs/stripe-setup" target="_blank" rel="noreferrer">Stripe導入手順を開く</a>
                    </div>
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setShowDocsModal(false)}>閉じる</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* AI利用制限の選択モーダル（プラン変更 or 回数追加） */}
      {showLimitModal && (
        <div className="modal d-block" tabIndex={-1}>
          <div className="modal-dialog">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">AI利用制限の変更</h5>
                <button type="button" className="btn-close" onClick={() => setShowLimitModal(false)}></button>
              </div>
              <div className="modal-body">
                <div className="vstack gap-3">
                  <div className="p-2 border rounded">
                    <div className="fw-bold">プランを変更</div>
                    <div className="text-muted" style={{ fontSize: '0.9rem' }}>月間のAI利用上限を変更します</div>
                    <div className="text-muted" style={{ fontSize: '0.85rem' }}>※ プランはサブスクリプションではなく買い切りです。回数の追加は「買い切り」のクレジットで対応できます。</div>
                    <div className="mt-2">
                      <button className="btn btn-outline-primary" onClick={() => { setPlanError(null); setShowLimitModal(false); setShowPlanModal(true); }}>プラン変更を開く</button>
                    </div>
                  </div>
                  <div className="p-2 border rounded">
                    <div className="fw-bold">回数を追加（買い切り）</div>
                    <div className="text-muted" style={{ fontSize: '0.9rem' }}>今月の残り回数にクレジットを加算します</div>
                    <div className="mt-2 d-flex flex-wrap gap-2">
                      {creditPacksError && (
                        <div className="alert alert-warning w-100 py-2">{creditPacksError}</div>
                      )}
                      {creditPacksLoading && (
                        <div className="text-muted">読み込み中...</div>
                      )}
                      {!creditPacksLoading && creditPacks.map((p) => (
                        <button key={p.creditPackSid} className="btn btn-outline-success" disabled={creditBuying}
                          onClick={async () => {
                            try { setCreditBuying(true); const { sessionUrl } = await createCreditCheckout(p.stripePriceId); window.location.href = sessionUrl; }
                            catch (e: unknown) { const errMsg = (e as { response?: { data?: { message?: string } } ; message?: string })?.response?.data?.message ?? (e as { message?: string }).message ?? 'クレジット購入に失敗しました'; setAiError(errMsg); }
                            finally { setCreditBuying(false); }
                          }}>
                          +{p.amount}回
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setShowLimitModal(false)}>閉じる</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};