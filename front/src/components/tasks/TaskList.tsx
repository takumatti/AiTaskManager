import type { Task } from "../../types/task";
import { useState, useMemo, useEffect } from "react";
import { decomposeTask, fetchTaskTree } from "../../api/taskApi";
import type { TaskTreeNode } from "../../api/taskApi";
import "./TaskList.css";

// タスク一覧コンポーネント
export const TaskList = ({
  tasks,
  onDelete,
  onEdit,
}: {
  tasks: Task[];
  onDelete: (id: number) => void;
  onEdit: (task: Task) => void;
}) => {
  const [tree, setTree] = useState<TaskTreeNode[] | null>(null);
  const [expanded, setExpanded] = useState<Record<number, boolean>>({});
  const [highlightIds, setHighlightIds] = useState<Set<number>>(new Set());
  const [loadingRedecompose, setLoadingRedecompose] = useState<number | null>(null);

  // 初回はフロット配列のみ表示。ユーザーが"階層表示"操作をしたらtree取得する想定だが、簡易版で自動ロード
  useMemo(() => {
    // ツリー未取得なら試行
    if (!tree) {
      fetchTaskTree().then(setTree).catch(e => console.warn("tree load fail", e));
    }
  }, [tree]);

  // tasks が変化したらツリーを再取得（新規作成/更新/削除後に反映）
  // 変更検出を件数だけでなく主要フィールドからのシグネチャで行う
  const tasksSignature = useMemo(() => {
    try {
      return tasks
        .map(t => `${t.id}|${t.title}|${t.updatedAt ?? ''}|${t.status ?? ''}|${t.priority ?? ''}|${t.dueDate ?? ''}`)
        .join(";");
    } catch {
      return String(tasks.length);
    }
  }, [tasks]);

  useEffect(() => {
    fetchTaskTree()
      .then(setTree)
      .catch(e => console.warn("tree reload fail", e));
  }, [tasksSignature]);

  const handleToggle = (id: number) => {
    setExpanded(prev => ({ ...prev, [id]: !prev[id] }));
  };

  const handleDeleteWithConfirm = (node: TaskTreeNode) => {
    const childCount = node.children?.length ?? 0;
    const msg = childCount > 0
      ? `このタスクには子タスクが ${childCount} 件あります。親と全ての子孫タスクが削除されます。よろしいですか？`
      : `このタスクを削除します。よろしいですか？`;
    if (window.confirm(msg)) {
      onDelete(node.id);
    }
  };

  const handleRedecompose = async (node: TaskTreeNode) => {
    setLoadingRedecompose(node.id);
    try {
  const newTree = await decomposeTask(node.id, { description: node.description });
      setTree(newTree);
      // 新しい子IDをハイライト（親の直下のみ）
      const parent = newTree.find(p => p.id === node.id);
      if (parent) {
        const ids = new Set(parent.children.map(c => c.id));
        setHighlightIds(ids);
        setExpanded(prev => ({ ...prev, [node.id]: true }));
        // 3秒後にハイライト解除
        setTimeout(() => setHighlightIds(new Set()), 3000);
      }
    } catch (e) {
      console.error("redecompose fail", e);
    } finally {
      setLoadingRedecompose(null);
    }
  };

  const renderNode = (node: TaskTreeNode, depth: number) => {
    const hasChildren = node.children && node.children.length > 0;
    const isExpanded = expanded[node.id];
    const indentStyle = { marginLeft: depth * 16 };
    const canDecompose = depth < 3; // 0:root,1,2,3(=第4階層) → 第4階層では不可
    return (
      <div key={node.id} className={`task-tree-item ${highlightIds.has(node.id) ? "new-child-highlight" : ""}`}>
        <div className="task-tree-inner">
          <div className="task-tree-row">
          <div className="task-tree-main" style={indentStyle}>
            {hasChildren && (
              <button className="collapse-btn" onClick={() => handleToggle(node.id)}>
                {isExpanded ? "▼" : "▶"}
              </button>
            )}
            {!hasChildren && <span className="collapse-placeholder" />}
            <span className="task-title" onClick={() => onEdit(convertNodeToTask(node))}>{node.title}</span>
          </div>
            <div className="task-tree-actions">
            <span className={`count-chip ${hasChildren ? "" : "empty"}`}>
              {hasChildren ? <span className="child-count-badge">子{node.children.length}</span> : ""}
            </span>
            {loadingRedecompose === node.id ? (
              <span className="redecompose-loading">細分化中...</span>
            ) : (
              <button
                className="redecompose-btn"
                title={canDecompose ? "既存の子を作り直して再度細分化" : "階層は最大4までです"}
                onClick={() => canDecompose && handleRedecompose(node)}
                disabled={!canDecompose}
              >細分化</button>
            )}
            <button className="delete-btn" onClick={() => handleDeleteWithConfirm(node)}>削除</button>
            </div>
          </div>
          {hasChildren && isExpanded && (
            <div className="task-tree-children">
              {node.children.map(ch => renderNode(ch, depth + 1))}
            </div>
          )}
        </div>
      </div>
    );
  };

  const convertNodeToTask = (n: TaskTreeNode): Task => ({
    id: n.id,
    userId: n.userId,
    parentTaskId: n.parentTaskId ?? undefined,
    title: n.title,
    description: n.description,
    dueDate: n.dueDate,
    priority: n.priority as Task["priority"],
    status: n.status as Task["status"],
    createdAt: n.createdAt,
    updatedAt: n.updatedAt,
  });

  if (tree && tree.length > 0) {
    return <div className="task-tree-container">{tree.map(n => renderNode(n, 0))}</div>;
  }

  // フォールバック: 旧フラット表示
  if (tasks.length === 0) return <p>タスクがありません</p>;
  return <div className="task-list-fallback">{tasks.map(t => (
    <div key={t.id} className="task-flat-row">{t.title}</div>
  ))}</div>;
};