import { useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { isLive } from '../../lib/executions';
import { openTerminalWindow } from '../../lib/windowSync';
import { useAgentDockStore } from '../../store/AgentDockStore';
import styles from './RunningWorks.module.css';

/** 화면에 쓰는 상태 이름(백엔드 enum → 한국어). */
const LABEL: Record<string, string> = {
  QUEUED: '순서 대기',
  RUNNING: '작업 중',
  WAITING_CHILD: '하위 대기',
  WAITING_INPUT: '답 대기',
  DONE: '완료',
  FAILED: '실패',
  CANCELLED: '중단',
};

/** 사람이 필요한 것 → 실제로 도는 것 → 남에게 기다리는 것 → 아직 시작 전. */
const rank = (status: string) =>
  status === 'WAITING_INPUT' ? 0 : status === 'RUNNING' ? 1 : status === 'WAITING_CHILD' ? 2 : 3;

/**
 * 프로젝트 상세의 「실행 중인 작업」.
 *
 * <p>지금은 캔버스 노드 배지·하단 알약·실행 트리(최근 5개)로 흩어져 있어 "무엇이 돌고 있나"를 한눈에 보기 어렵다.
 * 여기서는 **트리(명령) 묶음**으로 보여 준다 — 한 명령이 13개 실행을 만든 실측이 있어 실행 단위로 나열하면 읽기 어렵다.
 *
 * <p>데이터는 서버의 **평평한 실행 목록**(`loadExecutionList`)을 그대로 쓴다. 새 API·새 SSE 연결이 없다
 * (스토어가 이벤트마다 같은 조건으로 다시 읽어 준다).
 */
export default function RunningWorks({ projectId }: { projectId: number }) {
  const { executionList, loadExecutionList, agents } = useAgentDockStore();
  const [open, setOpen] = useState(false);

  // 실행 목록은 프로젝트 전체를 넉넉히 받아 두고 화면에서 걸러 쓴다(서버 상태 이름과 화면 이름이 달라 필터는 여기서).
  useEffect(() => {
    loadExecutionList({ projectId, limit: 200 });
  }, [projectId, loadExecutionList]);

  const live = useMemo(() => executionList.filter((execution) => isLive(execution.status)), [executionList]);

  const trees = useMemo(() => {
    const byRoot = new Map<number, typeof live>();
    live.forEach((execution) => {
      const root = execution.rootExecutionId ?? execution.id;
      byRoot.set(root, [...(byRoot.get(root) ?? []), execution]);
    });
    return [...byRoot.entries()]
      .map(([rootId, rows]) => ({
        rootId,
        rows: [...rows].sort((left, right) => rank(left.status) - rank(right.status) || left.id - right.id),
      }))
      .sort(
        (left, right) =>
          rank(left.rows[0].status) - rank(right.rows[0].status) || left.rootId - right.rootId,
      );
  }, [live]);

  const nameOf = (id: number) => agents.find((agent) => agent.id === id)?.name ?? '에이전트';
  if (live.length === 0) return null;

  return (
    <>
      <button
        type="button"
        className={styles.chip}
        onClick={() => setOpen(true)}
        title="지금 돌고 있는 작업들 — 트리 묶음으로 봅니다"
      >
        실행 중 {live.length}
      </button>

      {open &&
        createPortal(
          // 뒤를 흐리지 않는다 — 빈 곳을 누르면 닫힌다(요청 창과 같은 규칙).
          <div className={styles.backdrop} onClick={() => setOpen(false)}>
            <aside
              className={styles.drawer}
              role="dialog"
              aria-label="실행 중인 작업"
              onClick={(event) => event.stopPropagation()}
            >
              <header className={styles.head}>
                <strong>실행 중인 작업 {live.length}건</strong>
                <span className={styles.muted}>트리 {trees.length}개</span>
                <button type="button" className={styles.close} onClick={() => setOpen(false)}>
                  닫기
                </button>
              </header>

              <ul className={styles.trees}>
                {trees.map((tree) => (
                  <li key={tree.rootId} className={styles.tree}>
                    <div className={styles.treeHead}>
                      <span className={styles.treeId}>#{tree.rootId}</span>
                      <span className={styles.treeName}>{nameOf(tree.rows[0].agentId)}</span>
                      <span className={styles.treeCount}>실행 {tree.rows.length}건</span>
                    </div>
                    {/* 무엇을 시킨 명령인지 — 실행 목록이 태스크 제목을 함께 준다. */}
                    {tree.rows[0].prompt !== '' && <p className={styles.treeTitle}>{tree.rows[0].prompt}</p>}
                    <ul className={styles.rows}>
                      {tree.rows.map((row) => (
                        <li key={row.id}>
                          <button
                            type="button"
                            className={styles.row}
                            onClick={() => openTerminalWindow(row.agentId)}
                            title="터미널 열기"
                          >
                            <span className={styles.rowId}>#{row.id}</span>
                            <span className={styles.rowName}>{nameOf(row.agentId)}</span>
                            <span className={styles.rowState}>{LABEL[row.status] ?? row.status}</span>
                          </button>
                        </li>
                      ))}
                    </ul>
                  </li>
                ))}
              </ul>
            </aside>
          </div>,
          document.body,
        )}
    </>
  );
}
