import { CSSProperties, useState } from 'react';
import { ChevronDownIcon, ChevronUpIcon } from '../../components/icons';
import { waitingInputs } from '../../lib/executions';
import { useAgentDockStore } from '../../store/AgentDockStore';
import type { Project } from '../../types';
import styles from './WaitingInput.module.css';
import { WaitingInputItem } from './WaitingInputPopup';

/**
 * 입력 대기 표시. 이 프로젝트의 실행 중 **사람의 답을 기다리는 것**이 하나라도 있으면
 * 구성도 **아래 가운데(확대/축소 도구 위)** 에 작은 알약으로 뜬다(왼쪽 패널이 아니라 캔버스 위).
 *
 * <p>기본은 접힌 알약이고, 누르면 질문·보기·답 입력까지 펼쳐진다 — 답을 기다리는 것이 있다는 사실은
 * 늘 보이되 화면을 가리지 않는다. 좌표는 확대/축소 도구와 같은 방식(`--inset-left`/`--inset-right`)으로
 * 좌우 패널을 뺀 빈 곳의 가운데에 맞춘다.
 *
 * <p>위임받은 자식 실행이 물은 것도 함께 센다(마스터만 보면 자식이 물은 질문을 놓친다).
 * 화면은 프로젝트 상세의 **항상 도는 다시 읽기**(3초)로 갱신되므로 답한 항목은 저절로 사라진다 — 새 주기는 없다.
 */
export default function WaitingInputBanner({
  project,
  insetLeft,
  insetRight,
}: {
  project: Project;
  /** 왼쪽에 떠 있는 에이전트/그룹 패널이 가리는 너비(px) */
  insetLeft: number;
  /** 오른쪽에 떠 있는 명령 패널이 가리는 너비(px) */
  insetRight: number;
}) {
  const { agents, executions } = useAgentDockStore();
  const [open, setOpen] = useState(false);

  const pending = waitingInputs(executions.filter((execution) => execution.projectId === project.id));
  if (pending.length === 0) return null;

  const nameOf = (agentId: number) => agents.find((agent) => agent.id === agentId)?.name ?? '에이전트';

  return (
    <div
      className={styles.dock}
      style={{ '--inset-left': `${insetLeft}px`, '--inset-right': `${insetRight}px` } as CSSProperties}
    >
      <section className={styles.banner} aria-label="입력 대기">
        <button
          type="button"
          className={styles.head}
          onClick={() => setOpen((prev) => !prev)}
          aria-expanded={open}
          title={open ? '접기' : '펼치기'}
        >
          <span className={styles.headTitle}>
            <span aria-hidden="true">💬</span> 입력 대기 {pending.length}건
          </span>
          <span className={styles.headChevron} aria-hidden="true">
            {open ? <ChevronUpIcon /> : <ChevronDownIcon />}
          </span>
        </button>
        {open && (
          <ul className={styles.list}>
            {pending.map((execution) => (
              <li key={execution.id}>
                <WaitingInputItem execution={execution} agentName={nameOf(execution.agentId)} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
