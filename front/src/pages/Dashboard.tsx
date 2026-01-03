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
  breakdownTask,
  type TaskBreakdownRequest,
  type TaskBreakdownResponse,
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

// 比較関数
const compareEpochAsc = (a: number, b: number) => {
  const aNaN = Number.isNaN(a);
  const bNaN = Number.isNaN(b);
  if (aNaN && bNaN) return 0;
  if (aNaN) return 1;
  if (bNaN) return -1;
  return a - b;
};

// 降順比較
const compareEpochDesc = (a: number, b: number) => compareEpochAsc(b, a);

// ダッシュボードコンポーネント
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
  // 一覧の表示モード（階層/フラット）
  const [listMode, setListMode] = useState<"tree" | "flat">("tree");
  // カレンダーセルから新規作成する際の初期期日
  const [initialDueDateForCreate, setInitialDueDateForCreate] = useState<string | undefined>(undefined);
  // 手動子作成用の親ID
  const [parentIdForCreate, setParentIdForCreate] = useState<number | undefined>(undefined);
  const [hideAiDecomposeOnCreate, setHideAiDecomposeOnCreate] = useState<boolean>(false);
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
  // AI細分化の結果表示用
  const [breakdownWarning, setBreakdownWarning] = useState<string | null>(null);
  const [breakdownPreview, setBreakdownPreview] = useState<Array<{ title: string; description?: string }>>([]);
  const [showBreakdownModal, setShowBreakdownModal] = useState(false);
  const [breakdownLoading, setBreakdownLoading] = useState(false);
  const [breakdownSelection, setBreakdownSelection] = useState<boolean[]>([]);
  const [creatingChildren, setCreatingChildren] = useState(false);
  const [lastCreatedTaskId, setLastCreatedTaskId] = useState<number | null>(null);
  const [lastCreatedTask, setLastCreatedTask] = useState<Task | null>(null);
  // AI細分化: 子ごとの優先度（初期値は親の優先度を継承）
  const [childPriorities, setChildPriorities] = useState<Array<"LOW" | "NORMAL" | "HIGH">>([]);
  // 一括変更用の優先度（初期値は親の優先度）
  const [bulkPriority, setBulkPriority] = useState<"LOW" | "NORMAL" | "HIGH">("NORMAL");
  // 子ごとの期限日（YYYY-MM-DD）。初期値は親の期限日、未設定は空文字
  const [childDueDates, setChildDueDates] = useState<string[]>([]);
  // type="date"用に yyyy/MM/dd -> yyyy-MM-dd へ正規化
  const normalizeDateForInput = (v?: string | null) => (v ? v.replace(/\//g, "-") : "");

  // タスクリストからの細分化要求（削除→AIプレビューを開く）
  const handleRedecomposeFromList = async (node: import("../api/taskApi").TaskTreeNode) => {
    try {
      setBreakdownLoading(true);
      // まず子孫削除
      await apiClient.delete(`/api/tasks/${node.id}/children`);
      // プレビュー→保存のフローで親参照が必要なため、親情報をセット
      setLastCreatedTaskId(node.id);
      const parentTask: Task = {
        id: node.id,
        userId: node.userId,
        parentTaskId: node.parentTaskId ?? undefined,
        title: node.title,
        description: node.description,
        dueDate: node.dueDate,
        priority: node.priority as Task["priority"],
        status: node.status as Task["status"],
        createdAt: node.createdAt,
        updatedAt: node.updatedAt,
      };
      setLastCreatedTask(parentTask);
      // AIプレビュー取得
      const prioForReq: "HIGH" | "NORMAL" | "LOW" | undefined =
        String(node.priority).toUpperCase() === "LOW" ? "LOW" :
        String(node.priority).toUpperCase() === "HIGH" ? "HIGH" :
        String(node.priority).toUpperCase() === "NORMAL" ? "NORMAL" : undefined;
      const req: TaskBreakdownRequest = {
        title: node.title,
        description: node.description ?? "",
        dueDate: node.dueDate,
        priority: prioForReq,
      };
      const resp: TaskBreakdownResponse = await breakdownTask(req);
      if (resp.warning && resp.warning.trim()) {
        setBreakdownWarning(resp.warning);
      }
      if (Array.isArray(resp.children) && resp.children.length > 0) {
        setBreakdownPreview(resp.children);
        setBreakdownSelection(new Array(resp.children.length).fill(true));
        // 優先度は親の優先度を継承（なければ NORMAL）
        const parentPri = (node.priority as TaskInput["priority"]) || "NORMAL";
        setChildPriorities(new Array(resp.children.length).fill(parentPri as "LOW" | "NORMAL" | "HIGH"));
        setBulkPriority(parentPri as "LOW" | "NORMAL" | "HIGH");
        // 期限日は親の期限日を継承（なければ空）
        const parentDueDashed = normalizeDateForInput(node.dueDate ?? "");
        setChildDueDates(new Array(resp.children.length).fill(parentDueDashed));
        setShowBreakdownModal(true);
      } else {
        setBreakdownPreview([]);
        setBreakdownSelection([]);
        setChildPriorities([]);
        setChildDueDates([]);
        setBulkPriority("NORMAL");
        setShowBreakdownModal(false);
        const hasExisting = !!(resp.warning && resp.warning.trim());
        setBreakdownWarning(
          hasExisting ? resp.warning! : "AIによる子タスク提案がありませんでした。説明をもう少し具体的にすると分解が成功しやすくなります。"
        );
      }
    } catch (e) {
      console.error("[Dashboard] redecompose from list failed", e);
      const message = (e as Error)?.message || "AI細分化の呼び出しに失敗しました。時間をおいて再試行してください。";
      setBreakdownWarning(message);
      setShowBreakdownModal(false);
    } finally {
      setBreakdownLoading(false);
    }
  };
  
  const allSelect = (checked: boolean) => {
    setBreakdownSelection(prev => prev.map(() => checked));
  };

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
    let createdTask: Task | null = null;
    try {
      createdTask = await createTask(input);
      const tasks = await fetchTasks();
      setAllTasks(tasks);
      setDataVersion((v) => v + 1);
      setShowForm(false);
      setEditingTask(null);
      setLastCreatedTaskId(createdTask?.id ?? null);
      setLastCreatedTask(createdTask ?? null);
      console.debug("lastCreatedTaskId", createdTask?.id);

      // 新規作成後にAI細分化（チェックON時のみプレビューを開く）
      if ((input as unknown as { ai_decompose?: boolean }).ai_decompose) {
        try {
          setBreakdownLoading(true);
          const prioForReq: "HIGH" | "NORMAL" | "LOW" | undefined =
            input.priority === "LOW" ? "LOW" :
            input.priority === "NORMAL" ? "NORMAL" :
            input.priority === "HIGH" ? "HIGH" : undefined;
          const req: TaskBreakdownRequest = {
            title: input.title,
            description: input.description ?? "",
            dueDate: input.due_date,
            priority: prioForReq,
          };
          console.debug("[Dashboard] breakdown request", req);
          // 仕様: 細分化ボタン押下時は既存の子・孫タスクを削除してからプレビューを開く
          try {
            if (createdTask?.id != null) {
              await apiClient.delete(`/api/tasks/${createdTask.id}/children`);
              console.debug("[Dashboard] deleted existing children/grandchildren for", createdTask.id);
            }
          } catch (delErr) {
            console.warn("[Dashboard] failed to delete existing children before breakdown", delErr);
            // 削除失敗でもプレビューは試みる（サーバ側で権限や存在チェック済み）
          }
          const resp: TaskBreakdownResponse = await breakdownTask(req);
          console.debug("[Dashboard] breakdown response children", resp.children?.length ?? 0, resp);
          // 警告があればフォーム上部に表示（ダッシュボードヘッダー直下）
          if (resp.warning && resp.warning.trim()) {
            setBreakdownWarning(resp.warning);
          }
          // 子候補があればプレビューモーダルを開く／なければ警告を明示
          if (Array.isArray(resp.children) && resp.children.length > 0) {
            setBreakdownPreview(resp.children);
            setBreakdownSelection(new Array(resp.children.length).fill(true));
            // 初期の優先度は親の優先度を継承（なければ NORMAL）
            const parentPri = (createdTask?.priority as TaskInput["priority"]) || "NORMAL";
            setChildPriorities(new Array(resp.children.length).fill(parentPri as "LOW" | "NORMAL" | "HIGH"));
            setBulkPriority((parentPri as "LOW" | "NORMAL" | "HIGH"));
            // 初期の期限日は親の期限日を継承（なければ空）
            const parentDue = createdTask?.dueDate ?? "";
            const parentDueDashed = normalizeDateForInput(parentDue);
            setChildDueDates(new Array(resp.children.length).fill(parentDueDashed));
            // 既存の警告は一旦クリアして新しいプレビューに集中
            setBreakdownWarning(null);
            setBreakdownLoading(false);
            setShowBreakdownModal(true);
          } else {
            // 既存のwarningが空ならデフォルト文言を表示して親のみ作成を通知
            setBreakdownPreview([]);
            setBreakdownSelection([]);
            setChildPriorities([]);
            setChildDueDates([]);
            setBulkPriority("NORMAL");
            setShowBreakdownModal(false);
            {
              const hasExisting = !!(resp.warning && resp.warning.trim());
              setBreakdownWarning(
                hasExisting
                  ? resp.warning!
                  : "AIによる子タスク提案がありませんでした。親タスクのみ作成しています。説明をもう少し具体的にすると分解が成功しやすくなります。"
              );
            }
            setBreakdownLoading(false);
          }
        } catch (e) {
          console.error("AI細分化の呼び出しに失敗しました", e);
          const message = (e as Error)?.message || "AI細分化の呼び出しに失敗しました。時間をおいて再試行してください。";
          setBreakdownWarning(message);
          // エラー時はモーダルを閉じて操作を解除
          setShowBreakdownModal(false);
          setBreakdownLoading(false);
        }
      }
    } catch (error) {
      console.error("作成エラー:", error);
    }
  };

  // 細分化プレビューの選択切り替え
  const toggleSelection = (idx: number) => {
    setBreakdownSelection(prev => prev.map((v, i) => (i === idx ? !v : v)));
  };

  // 選択した子タスクを一括作成
  const createSelectedChildren = async () => {
    console.debug("[Dashboard] createSelectedChildren parent id used", lastCreatedTaskId)
    if (!lastCreatedTaskId) {
      setBreakdownWarning("親タスクの作成情報が見つかりません。もう一度お試しください。");
      // 親未確定時はモーダルを閉じて操作を中断
      setShowBreakdownModal(false);
      return;
    }
    const selected = breakdownPreview
      .map((c, idx) => ({ c, idx }))
      .filter(({ idx }) => breakdownSelection[idx]);
    console.debug("[Dashboard] breakdownPreview", breakdownPreview);
    console.debug("[Dashboard] breakdownSelection", breakdownSelection);
    console.debug("[Dashboard] selected children count", selected.length);
    if (selected.length === 0) {
      setBreakdownWarning("作成対象の子タスクが選択されていません。");
      return;
    }
    setCreatingChildren(true);
    try {
  for (const { c, idx } of selected) {
        // 子ごとに選択された優先度（未設定なら親→NORMAL）
        const priorityForChild: TaskInput["priority"] = (childPriorities[idx] as TaskInput["priority"]) || (lastCreatedTask?.priority as TaskInput["priority"]) || "NORMAL";
        const statusForChild: TaskInput["status"] = (lastCreatedTask?.status as TaskInput["status"]) || "TODO";
        const childInput: TaskInput = {
          title: c.title,
          description: c.description ?? "",
          parent_task_id: lastCreatedTaskId,
          parentTaskId: lastCreatedTaskId as number,
          // AI 提案から作成された子タスク（初回のみカウント対象）
          ai_generated: idx === selected[0].idx ? (true as unknown as boolean) : undefined,
          // 親の属性を継承（未設定なら既定値）
          priority: priorityForChild,
          status: statusForChild,
          due_date: (childDueDates[idx] ?? normalizeDateForInput(lastCreatedTask?.dueDate ?? "") ?? "") || undefined,
        };
        console.debug("[Dashboard] childInput payload", childInput);
        await createTask(childInput);
      }
      // 再取得して画面反映
      const tasks = await fetchTasks();
      setAllTasks(tasks);
      setDataVersion(v => v + 1);
    // 子タスク作成により AI 使用回数が増えるため、クォータを再取得して表示を更新
    await reloadQuota();
      setPlanMessage({ type: 'success', text: '選択した子タスクを作成しました' });
      // モーダルを閉じる
      setShowBreakdownModal(false);
      setBreakdownPreview([]);
      setBreakdownSelection([]);
      setChildPriorities([]);
  setChildDueDates([]);
      setBulkPriority("NORMAL");
    } catch (e) {
      console.error("子タスクの一括作成に失敗", e);
      // サーバーからのメッセージ（例: 親タスクが見つかりませんでした）を優先表示
      const message = (e as Error)?.message || "子タスクの一括作成に失敗しました。時間をおいて再試行してください。";
      setBreakdownWarning(message);
      // エラー時もモーダルを閉じられるようにする
      setShowBreakdownModal(false);
    } finally {
      setCreatingChildren(false);
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
          {breakdownWarning && (
            <div className="alert alert-warning py-2" style={{ marginTop: 8 }}>
              <div style={{ whiteSpace: 'pre-wrap' }}>{breakdownWarning}</div>
              <button type="button" className="btn btn-sm btn-outline-secondary ms-2" onClick={() => setBreakdownWarning(null)}>閉じる</button>
            </div>
          )}
          {breakdownLoading && (
            <div className="alert alert-info py-2 d-flex align-items-center gap-2" style={{ marginTop: 8 }}>
              <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
              <span>AI が子タスクを作成しています…</span>
            </div>
          )}

            <div className="dashboard-header-sub" style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              {/* 左端：表示切替ボタン＋その下にリスト表示モードトグル（縦並び） */}
              <div className="dashboard-view-toggle-left" style={{ flex: '0 0 auto', display: 'flex', flexDirection: 'column', gap: 8 }}>
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
                {view === "list" && (
                  <button
                    className="btn btn-outline-secondary"
                    style={{ whiteSpace: 'nowrap', minHeight: 36, width: 120, textAlign: 'center' }}
                    onClick={() => setListMode(listMode === "flat" ? "tree" : "flat")}
                    title="一覧の表示形式を切替（階層/フラット）"
                  >
                    {listMode === "flat" ? "階層表示" : "フラット表示"}
                  </button>
                )}
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
                                <div>
                                  <span className="text-muted">次回リセット日:</span> <span className="fw-semibold">{aiQuota.resetDate}</span>
                                </div>
                                {typeof aiQuota.daysUntilReset === 'number' && (
                                  <div className="text-muted">（切替まで残り {aiQuota.daysUntilReset} 日）</div>
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
                                <div>
                                  <span className="text-muted">次回リセット日:</span> <span className="fw-semibold">{aiQuota.resetDate}</span>
                                </div>
                                {typeof aiQuota.daysUntilReset === 'number' && (
                                  <div className="text-muted">（切替まで残り {aiQuota.daysUntilReset} 日）</div>
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

        {/* フィルタ（ツリー表示時は全体非活性、カレンダー表示時は並び替えのみ非活性）*/}
        <div className="dashboard-filters-card">
          {/* ツリー表示中は全体無効化 */}
          <div className="dashboard-filters-row" style={listMode === "tree" ? { pointerEvents: 'none', opacity: 0.6 } : undefined}>
            <div style={{ flex: 1 }}>
              <div className="dashboard-filters-label">ステータス</div>
              <TaskFilters status={status} priority={priority} onStatusChange={setStatus} onPriorityChange={setPriority} />
            </div>
            <div style={listMode === "tree" ? { width: 240 } : { width: 240, ...(view === "calendar" ? { pointerEvents: 'none', opacity: 0.6 } : {}) }}>
              <div className="dashboard-filters-label">並び替え</div>
              <TaskSort sort={sort} onSortChange={setSort} />
            </div>
          </div>
          {listMode === "tree" && (
            <div className="text-muted" style={{ marginTop: 6, fontSize: 13 }}>
              階層表示ではステータス・並び替えは適用されません。フラット表示に切り替えると反映されます。
            </div>
          )}
          {view === "calendar" && (
            <div className="text-muted" style={{ marginTop: 6, fontSize: 13 }}>
              カレンダー表示では並び替えは適用されません。ステータス・優先度のフィルタは利用できます。
            </div>
          )}
        </div>

        {/* リスト or カレンダー */}
        {view === "list" ? (
          <>
            <TaskList
              key={`list-${dataVersion}`}
              tasks={filteredTasks}
              onEdit={handleEdit}
              onDelete={handleDelete}
              onRedecompose={handleRedecomposeFromList}
              onCreateChild={(parentId: number, depth: number) => {
                // 子追加: 親IDをセットして新規作成フォームを開く
                setEditingTask(null);
                setParentIdForCreate(parentId);
                // ユーザー認識の階層（親=1）に合わせ、3階層目ではAI細分化を非表示
                setHideAiDecomposeOnCreate(depth >= 2);
                setInitialDueDateForCreate(undefined);
                setShowForm(true);
              }}
              forceFlat={listMode === "flat"}
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
            // 3階層目の子追加ではAI細分化チェックを非表示
            hideAiDecompose={hideAiDecomposeOnCreate}
            onClose={() => { setShowForm(false); setEditingTask(null); setInitialDueDateForCreate(undefined); setParentIdForCreate(undefined); }}
          />
        )}
        {/* AI細分化プレビューモーダル */}
        {showBreakdownModal && (
          <div className="modal d-block" tabIndex={-1}>
            <div className="modal-dialog">
              <div className="modal-content">
                <div className="modal-header">
                  <h5 className="modal-title">AI細分化の提案</h5>
                  <button type="button" className="btn-close" onClick={async () => { setShowBreakdownModal(false); setBreakdownPreview([]); const tasks = await fetchTasks(); setAllTasks(tasks); setDataVersion(v => v + 1); }}></button>
                </div>
                <div className="modal-body">
                  {breakdownPreview.length === 0 ? (
                    <div className="text-muted">提案がありません</div>
                  ) : (
                    <div className="vstack gap-2" style={{ maxHeight: '400px', overflowY: 'auto' }}>
                      <div className="d-flex justify-content-between align-items-center gap-2">
                        <div className="d-flex align-items-center gap-2">
                          <label className="form-label mb-0">一括変更</label>
                          <select
                            className="form-select form-select-sm"
                            style={{ width: 140 }}
                            value={bulkPriority}
                            onChange={(e) => setBulkPriority(e.target.value as "LOW" | "NORMAL" | "HIGH")}
                          >
                            <option value="HIGH">高</option>
                            <option value="NORMAL">中</option>
                            <option value="LOW">低</option>
                          </select>
                          <button
                            className="btn btn-sm btn-outline-primary"
                            disabled={!breakdownSelection.some(Boolean)}
                            onClick={() => {
                              setChildPriorities(prev => prev.map((v, i) => (breakdownSelection[i] ? bulkPriority : v)));
                            }}
                          >選択に適用</button>
                          <button
                            className="btn btn-sm btn-outline-primary"
                            onClick={() => {
                              setChildPriorities(prev => prev.map(() => bulkPriority));
                            }}
                          >全てに適用</button>
                        </div>
                        <div className="d-flex justify-content-end gap-2">
                          <button className="btn btn-sm btn-outline-secondary" onClick={() => allSelect(true)}>全選択</button>
                          <button className="btn btn-sm btn-outline-secondary" onClick={() => allSelect(false)}>全解除</button>
                        </div>
                      </div>
                      <div className="text-muted" style={{ fontSize: '0.85rem' }}>
                        親の期限日: <span className="fw-semibold">{lastCreatedTask?.dueDate ?? 'なし'}</span>
                      </div>
                      {breakdownPreview.map((c, idx) => (
                        <div key={idx} className="p-2 border rounded d-flex align-items-start gap-2">
                          <input type="checkbox" className="form-check-input mt-1" checked={breakdownSelection[idx] ?? false} onChange={() => toggleSelection(idx)} />
                          <div className="flex-grow-1">
                            <div className="fw-bold">{c.title}</div>
                            {c.description && (<div className="text-muted" style={{ whiteSpace: 'pre-wrap' }}>{c.description}</div>)}
                          </div>
                          <div className="d-flex align-items-end gap-2 flex-column" style={{ minWidth: 160, width: '100%' }}>
                            <div className="d-flex flex-column" style={{ width: 160 }}>
                              <label className="form-label mb-1 fw-semibold text-start" style={{ whiteSpace: 'nowrap' }}>優先度</label>
                              <select
                                className="form-select form-select-sm"
                                style={{ width: 160 }}
                                value={childPriorities[idx] ?? ((lastCreatedTask?.priority as TaskInput["priority"]) || "NORMAL")}
                                onChange={(e) => {
                                  const v = e.target.value as "LOW" | "NORMAL" | "HIGH";
                                  setChildPriorities(prev => {
                                    const next = [...prev];
                                    next[idx] = v;
                                    return next;
                                  });
                                }}
                              >
                                <option value="HIGH">高</option>
                                <option value="NORMAL">中</option>
                                <option value="LOW">低</option>
                              </select>
                            </div>
                            <div className="d-flex flex-column" style={{ width: 160 }}>
                              <label className="form-label mb-1 fw-semibold text-start" style={{ whiteSpace: 'nowrap' }}>期限日</label>
                              <input
                                type="date"
                                className="form-control form-control-sm"
                                style={{ width: 160 }}
                                value={normalizeDateForInput(childDueDates[idx] ?? (lastCreatedTask?.dueDate ?? ""))}
                                onChange={(e) => {
                                  const v = e.target.value || "";
                                  setChildDueDates(prev => {
                                    const next = [...prev];
                                    next[idx] = v;
                                    return next;
                                  });
                                }}
                              />
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
                <div className="modal-footer">
                  <button className="btn btn-outline-secondary" onClick={async () => { setShowBreakdownModal(false); setBreakdownPreview([]); setBreakdownSelection([]); const tasks = await fetchTasks(); setAllTasks(tasks); setDataVersion(v => v + 1); }}>閉じる</button>
                  <button className="btn btn-primary" disabled={creatingChildren} onClick={createSelectedChildren}>
                    {creatingChildren ? "作成中..." : "選択した子を作成"}
                  </button>
                </div>
              </div>
            </div>
          </div>
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
                  <br />
                  ※ Proなどの有料プランから無料プランへ変更する場合、当月の未使用分はボーナス回数として加算されます。
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