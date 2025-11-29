// ユーティリティ（モジュールスコープに配置）
// ISO 8601（例: "2025-11-24T09:15:00+00:00"）をミリ秒に変換
const parseIsoToEpoch = (s?: string | null): number => {
  if (!s) return NaN;
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
import { TaskFilters } from "../components/tasks/TaskFilters";
import { TaskSort } from "../components/tasks/TaskSort";
import { TaskCreateForm } from "../components/tasks/TaskCreateForm";
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
  // 編集中タスク
  const [editingTask, setEditingTask] = useState<Task | null>(null);
  // モーダル表示
  const [showForm, setShowForm] = useState(false);

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

  // フィルタ＆ソート
  const filteredTasks = useMemo(() => {
    // フィルタ
    const list = allTasks.filter((t) => {
      if (status && t.status !== status) return false;
      if (priority && t.priority !== priority) return false;
      return true;
    });

    // ソート
    list.sort((a, b) => {
      const dueA = parseIsoToEpoch(a?.due_date);
      const dueB = parseIsoToEpoch(b?.due_date);
      const createdA = parseIsoToEpoch(a?.created_at);
      const createdB = parseIsoToEpoch(b?.created_at);

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
  }, [status, priority, sort, allTasks]);

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
    } catch (e) {
      console.error("削除エラー:", e);
    }
  };

  return (
    <div className="dashboard-container">
      <div className="dashboard-card">
        <div className="dashboard-header">
          <div className="dashboard-title">タスク一覧</div>

          <div className="dashboard-actions">
            <button
              className="btn btn-primary"
              onClick={() => {
                setEditingTask(null);
                setShowForm(true);
              }}
            >
              新規タスク
            </button>

            <button
              className="btn btn-outline-secondary"
              onClick={async () => {
                await logout();
                navigate("/login");
              }}
            >
              ログアウト
            </button>
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

        <TaskList tasks={filteredTasks} onDelete={handleDelete} onEdit={handleEdit} />

        {/* 新規 or 編集モーダル */}
        {showForm && (
          <TaskCreateForm
            editingTask={editingTask}
            onCreated={editingTask ? undefined : handleCreated}
            onUpdated={editingTask ? handleUpdate : undefined}
            onClose={() => {
              setShowForm(false);
              setEditingTask(null);
            }}
          />
        )}
      </div>
    </div>
  );
};

export default Dashboard;