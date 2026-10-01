import { useState } from 'react';
import { ChevronDownIcon, ChevronUpIcon } from '../../components/icons';
import { waitingInputs } from '../../lib/executions';
import { useAgentDockStore } from '../../store/AgentDockStore';
import type { Project } from '../../types';
import styles from './WaitingInput.module.css';
import { WaitingInputItem } from './WaitingInputPopup';

/**
 * 입력 대기 배너. 이 프로젝트의 실행 중 **사람의 답을 기다리는 것**이 하나라도 있으면 헤더 카드 아래에 뜬다.
 *
 * <p>위임받은 자식 실행이 물은 것도 함께 센다(마스터만 보면 자식이 물은 질문을 놓친다). 펼치면 각 질문을
 * 물은 에이전트 이름과 함께 보여 주고, 보기(있으면)를 버튼으로, 자유 입력을 입력줄로 그 자리에서 답한다.
 * 화면은 프로젝트 상세의 **항상 도는 다시 읽기**(3초)로 갱신되므로 답한 항목은 저절로 사라진다 — 새 주기는 없다.
 */
export default function WaitingInputBanner({ project }: { project: Project }) {
  const { agents, executions } = useAgentDockStore();
  const [open, setOpen] = useState(true);

  const pending = waitingInputs(executions.filter((execution) => execution.projectId === project.id));
  if (pending.length === 0) return null;

  const nameOf = (agentId: number) => agents.find((agent) => agent.id === agentId)?.name ?? '에이전트';

  return (
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
  );
}
