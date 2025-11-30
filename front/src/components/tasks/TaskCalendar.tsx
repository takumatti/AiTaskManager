import React, { useMemo, useState, useRef, useEffect } from "react";
import type { Task } from "../../types/task";
import "./TaskCalendar.css";
import { TaskTooltip } from "./TaskTooltip";
// formatDateKey をローカル定義（祝日API取得で十分なため）
const formatDateKey = (d: Date): string => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
import { fetchHolidays } from "../../api/holidayApi";
import type { PublicHoliday } from "../../api/holidayApi";

// モジュールスコープの祝日キャッシュ（コンポーネント再マウント時も維持）
const holidayCache: Record<number, { set: Set<string>; names: Record<string, string> }> = {};
let holidayInFlight = false;

// 柔軟な日付文字列をミリ秒に変換（Dashboardと同等ロジックを局所的に複製）
const parseDateFlexibleToEpoch = (s?: string | null): number => {
  if (!s) return NaN;
  const m1 = s.match(/^(\d{4})\/(\d{2})\/(\d{2})$/);
  if (m1) return new Date(Number(m1[1]), Number(m1[2]) - 1, Number(m1[3])).getTime();
  const m2 = s.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (m2) return new Date(Number(m2[1]), Number(m2[2]) - 1, Number(m2[3])).getTime();
  const ms = Date.parse(s);
  return Number.isNaN(ms) ? NaN : ms;
};

interface TaskCalendarProps {
  tasks: Task[];
  month: Date; // 表示している月の任意の日(推奨: 1日)
  onPrev: () => void;
  onNext: () => void;
  onEdit: (task: Task) => void;
  onCreate?: (dateISO: string) => void; // カレンダーセルクリックで新規作成（yyyy-MM-dd）
}


// 月の週配列生成(日曜開始)
const buildMonthMatrix = (month: Date): Date[][] => {
  const year = month.getFullYear();
  const monthIndex = month.getMonth();
  const first = new Date(year, monthIndex, 1);
  const last = new Date(year, monthIndex + 1, 0); // 月末
  const matrix: Date[][] = [];

  // 先頭週: first から前方に遡り日曜まで埋める
  const cursor = new Date(first); // プロパティ変更のみなのでconstで問題なし
  cursor.setDate(cursor.getDate() - cursor.getDay()); // 同週日曜

  while (cursor <= last || cursor.getDay() !== 0) { // 月末を過ぎても週の最後(土曜)まで
    const week: Date[] = [];
    for (let i = 0; i < 7; i++) {
      week.push(new Date(cursor));
      cursor.setDate(cursor.getDate() + 1);
    }
    matrix.push(week);
  }
  return matrix;
};

// 指定日付とタスクdueDateが一致するか判定
const isSameDay = (d: Date, due: string | undefined): boolean => {
  if (!due) return false;
  const ms = parseDateFlexibleToEpoch(due);
  if (Number.isNaN(ms)) return false;
  const target = new Date(ms);
  return (
    target.getFullYear() === d.getFullYear() &&
    target.getMonth() === d.getMonth() &&
    target.getDate() === d.getDate()
  );
};

export const TaskCalendar: React.FC<TaskCalendarProps> = ({ tasks, month, onPrev, onNext, onEdit, onCreate }) => {
  const monthMatrix = useMemo(() => buildMonthMatrix(month), [month]);
  const today = new Date();
  const year = month.getFullYear();
  const [tooltipTaskId, setTooltipTaskId] = useState<number | null>(null);
  const [tooltipPos, setTooltipPos] = useState<{ x: number; y: number } | null>(null);
  const calendarRef = useRef<HTMLDivElement | null>(null);
  // 祝日動的取得用（フォールバックは廃止）
  const [holidaySet, setHolidaySet] = useState<Set<string>>(() => {
    const cached = holidayCache[month.getFullYear()];
    return cached ? cached.set : new Set();
  });
  const [holidayNameMap, setHolidayNameMap] = useState<Record<string,string>>(() => {
    const cached = holidayCache[month.getFullYear()];
    return cached ? cached.names : {};
  });
  // ローディングフラグ不要（フォールバックで即描画するため）

  // 日ごとタスクマップ (キー: yyyy-mm-dd)
  const tasksByDate = useMemo(() => {
    const map: Record<string, Task[]> = {};
    for (const t of tasks) {
      if (!t.dueDate) continue;
      const ms = parseDateFlexibleToEpoch(t.dueDate);
      if (Number.isNaN(ms)) continue;
      const d = new Date(ms);
      const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
      if (!map[key]) map[key] = [];
      map[key].push(t);
    }
    return map;
  }, [tasks]);

  // overdue判定
  const isOverdue = (task: Task): boolean => {
    if (!task.dueDate) return false;
    if (task.status === 'DONE') return false;
    const ms = parseDateFlexibleToEpoch(task.dueDate);
    if (Number.isNaN(ms)) return false;
    const due = new Date(ms);
    const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    return due < todayStart;
  };

  // 外クリック判定はオーバーレイ自体の onClick で処理するためリスナー不要

  // ツールチップ表示中はスクロール抑制（トップレベル）
  useEffect(() => {
    if (tooltipTaskId) {
      const prevOverflow = document.body.style.overflow;
      const prevPaddingRight = document.body.style.paddingRight;
      // 現在のスクロールバー幅を計測（差分で取得）
      const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
      document.body.style.overflow = 'hidden';
      if (scrollbarWidth > 0) {
        document.body.style.paddingRight = `${scrollbarWidth}px`;
      }
      return () => {
        document.body.style.overflow = prevOverflow;
        document.body.style.paddingRight = prevPaddingRight;
      };
    }
  }, [tooltipTaskId]);

  // 年が変わったタイミングで祝日取得（キャッシュ利用）
  useEffect(() => {
    // キャッシュヒット
    const cached = holidayCache[year];
    if (cached) {
      setHolidaySet(cached.set);
      setHolidayNameMap(cached.names);
      return;
    }
    if (holidayInFlight) {
      return; // 二重呼び出し抑止 (StrictMode等)
    }
    holidayInFlight = true;
    (async () => {
      try {
        const data: PublicHoliday[] = await fetchHolidays(year);
        if (data && data.length > 0) {
          const set = new Set<string>();
          const names: Record<string,string> = {};
          for (const h of data) {
            set.add(h.date);
            if (h.localName) names[h.date] = h.localName;
          }
          holidayCache[year] = { set, names };
          setHolidaySet(set);
          setHolidayNameMap(names);
        }
      } catch (e) {
        console.warn("祝日取得失敗", e);
      } finally {
        holidayInFlight = false;
      }
    })();
    return () => { holidayInFlight = false; };
  }, [year]);

  // 初回マウント時にも当年の祝日を確実に取得（キャッシュ未ヒット時のみ）
  useEffect(() => {
    const y = new Date().getFullYear();
    const cached = holidayCache[y];
    if (cached) return;
    if (holidayInFlight) return;
    holidayInFlight = true;
    (async () => {
      try {
        const data: PublicHoliday[] = await fetchHolidays(y);
        if (data && data.length > 0) {
          const set = new Set<string>();
          const names: Record<string,string> = {};
          for (const h of data) {
            set.add(h.date);
            if (h.localName) names[h.date] = h.localName;
          }
          holidayCache[y] = { set, names };
          // 表示中の年が初回取得年と一致していれば反映
          if (month.getFullYear() === y) {
            setHolidaySet(set);
            setHolidayNameMap(names);
          }
        }
      } catch (e) {
        console.warn("祝日初期取得失敗", e);
      } finally {
        holidayInFlight = false;
      }
    })();
    return () => { holidayInFlight = false; };
  }, [month, year]);

  return (
  <div className="task-calendar-wrapper" ref={calendarRef}>
      <div className="task-calendar-header">
        <button className="btn btn-sm btn-outline-secondary" onClick={onPrev}>‹ 前月</button>
        <div className="task-calendar-title">{month.getFullYear()}年 {month.getMonth() + 1}月</div>
        <button className="btn btn-sm btn-outline-secondary" onClick={onNext}>次月 ›</button>
      </div>

      <div className="task-calendar-grid">
        {['日','月','火','水','木','金','土'].map((w, idx) => (
          <div
            key={w}
            className={
              "task-calendar-weekday" +
              (idx === 0 ? " sunday" : "") +
              (idx === 6 ? " saturday" : "")
            }
          >{w}</div>
        ))}
        {monthMatrix.map((week, wi) => (
          <React.Fragment key={wi}>
            {week.map((day, di) => {
              const inCurrentMonth = day.getMonth() === month.getMonth();
              const key = formatDateKey(day);
              const dayTasks = tasksByDate[key] || [];
              const isToday = isSameDay(today, key);
              const dow = day.getDay();
              const isSunday = dow === 0;
              const isSaturday = dow === 6;
              const holidayFlag = holidaySet.has(key);
              const holidayName = holidayNameMap[key];
              const displayTasks = dayTasks.slice(0,3);
              const overflow = dayTasks.length - displayTasks.length;
              return (
                <div
                  key={di}
                  className={`task-calendar-cell${inCurrentMonth ? "" : " out-month"}${isToday ? " today" : ""}${(isSunday || holidayFlag) ? " sunday" : ""}${isSaturday ? " saturday" : ""}${holidayFlag ? " holiday" : ""}`}
                  onClick={() => {
                    if (!inCurrentMonth) return; // 当月以外は反応させない
                    const iso = key; // formatDateKeyはyyyy-MM-dd
                    if (onCreate) onCreate(iso);
                  }}
                >
                  <div className={`date-label${(isSunday || holidayFlag) ? " sunday" : ""}${isSaturday ? " saturday" : ""}${holidayFlag ? " holiday" : ""}`}>{day.getDate()}{holidayName && <div className="holiday-name" title={holidayName}>{holidayName}</div>}</div>
                  <div className="tasks-container">
                    {displayTasks.map(t => {
                      const overdue = isOverdue(t);
                      const priorityClass = overdue ? '' : ` priority-${t.priority.toLowerCase()}`;
                      return (
                        <div
                          key={t.id}
                          className={`task-pill clickable status-${t.status.toLowerCase()}${priorityClass}${overdue ? ' overdue' : ''}`}
                          onClick={(e) => {
                            e.stopPropagation();
                            if (t.id === tooltipTaskId) {
                              setTooltipTaskId(null);
                              setTooltipPos(null);
                              return;
                            }
                            const el = e.currentTarget as HTMLElement;
                            const rect = el.getBoundingClientRect();
                            const offsetX = 8; const offsetY = 8;
                            // viewport 座標で管理し overlay (fixed) にそのまま適用
                            let x = rect.left + offsetX;
                            let y = rect.top + offsetY;
                            const tooltipWidth = 220; // CSSと同期
                            const tooltipHeightApprox = 170; // だいたいの高さ
                            const viewportRight = window.innerWidth;
                            const viewportBottom = window.innerHeight;
                            // 右端はみ出し補正
                            if (x + tooltipWidth > viewportRight - 8) {
                              x = Math.max(8, viewportRight - tooltipWidth - 8);
                            }
                            // 下端はみ出し補正
                            if (y + tooltipHeightApprox > viewportBottom - 8) {
                              y = Math.max(8, viewportBottom - tooltipHeightApprox - 8);
                            }
                            setTooltipPos({ x, y });
                            setTooltipTaskId(t.id);
                          }}
                          aria-label={t.title}
                        >{t.title}</div>
                      );
                    })}
                    {/* セル内にはツールチップを描画せず、オーバーレイで表示 */}
                    {overflow > 0 && <div className="task-more">+{overflow}</div>}
                  </div>
                </div>
              );
            })}
          </React.Fragment>
        ))}
      </div>
        {/* グローバルオーバーレイ: ツールチップ表示中は背景操作禁止 */}
        {tooltipTaskId && tooltipPos && (
          <div className="task-tooltip-overlay" onClick={() => { setTooltipTaskId(null); setTooltipPos(null); }}>
            <div
              className="task-tooltip-wrapper"
              style={{ position:'absolute', left: tooltipPos.x, top: tooltipPos.y }}
              onClick={(e) => e.stopPropagation()}
            >
              <TaskTooltip
                task={tasks.find(t => t.id === tooltipTaskId)!}
                overdue={isOverdue(tasks.find(t => t.id === tooltipTaskId)!)}
                onEdit={(task) => { setTooltipTaskId(null); setTooltipPos(null); onEdit(task); }}
                onClose={() => { setTooltipTaskId(null); setTooltipPos(null); }}
              />
            </div>
          </div>
        )}
    </div>
  );
};
