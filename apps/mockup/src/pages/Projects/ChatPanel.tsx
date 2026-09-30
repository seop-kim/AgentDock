import { FormEvent, useEffect, useRef, useState } from 'react';
import { unavailableReason } from '../../lib/agentAvailability';
import { useMockStore } from '../../store/MockStore';
import shared from '../../styles/shared.module.css';
import type { ChatTarget, Project } from '../../types';
import styles from './ChatPanel.module.css';
import { ExecutionSummaryCard } from './ExecutionTree';

const toValue = (target: ChatTarget | null) => (target ? `${target.kind}:${target.id}` : '');

const fromValue = (value: string): ChatTarget | null => {
  const [kind, id] = value.split(':');
  return kind === 'agent' || kind === 'group' ? { kind, id: Number(id) } : null;
};

/**
 * 구성도 아래에 떠 있는 채팅 창. 기본 대상은 프로젝트 **마스터 에이전트**이고, 원하면 그룹(리더가 받음)이나
 * 개별 에이전트를 직접 고를 수도 있다. 마스터에게 보내면 마스터가 팀 리더들에게 나눠 맡긴다(모의 응답).
 */
export default function ChatPanel({
  project,
  target,
  onTargetChange,
  expanded,
  onToggle,
  onSent,
  onOpenExecutions,
}: {
  project: Project;
  target: ChatTarget | null;
  onTargetChange: (target: ChatTarget | null) => void;
  /** 메시지 기록을 펼쳐서 보이는지 */
  expanded: boolean;
  onToggle: () => void;
  onSent: () => void;
  /** 응답에 딸린 실행 트리를 연다. */
  onOpenExecutions: (rootExecutionId: number) => void;
}) {
  const { agents, groups, providers, chats, executions, sendCommand } = useMockStore();
  const [text, setText] = useState('');
  const logRef = useRef<HTMLDivElement>(null);

  const projectAgents = agents.filter((a) => a.projectId === project.id);
  const projectGroups = groups.filter((g) => g.projectId === project.id);
  const projectExecutions = executions.filter((e) => e.projectId === project.id);
  const messages = chats.filter((m) => m.projectId === project.id);

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight });
  }, [messages.length, messages.filter((m) => m.status === 'pending').length, expanded]);

  const receiverHint = (() => {
    if (!target) return '명령을 받을 에이전트나 그룹을 고르세요.';
    if (target.kind === 'agent') {
      const agent = projectAgents.find((a) => a.id === target.id);
      if (!agent) return null;
      const reason = unavailableReason(agent, providers, project);
      if (reason) return `${agent.name}: ${reason}`;
      return agent.id === project.masterAgentId
        ? `${agent.name}(마스터)가 받아 팀(그룹) 리더들에게 나눠 맡깁니다.`
        : null;
    }
    const group = projectGroups.find((g) => g.id === target.id);
    const leader = projectAgents.find((a) => a.id === group?.leaderAgentId);
    if (!leader) return `${group?.name} 에는 리더가 없습니다.`;
    const reason = unavailableReason(leader, providers, project);
    return reason ? `리더 ${leader.name}: ${reason}` : `리더 ${leader.name} 이(가) 받습니다.`;
  })();

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const trimmed = text.trim();
    if (!target || trimmed === '') return;
    sendCommand(project.id, target, trimmed);
    setText('');
    onSent();
  };

  return (
    <section className={styles.chat} aria-label="에이전트 명령">
      <div className={styles.header}>
        <h2 className={styles.title}>명령</h2>
        <select
          className={styles.target}
          value={toValue(target)}
          onChange={(e) => onTargetChange(fromValue(e.target.value))}
          aria-label="명령 대상"
        >
          <option value="">대상 선택</option>
          <optgroup label="에이전트">
            {projectAgents.map((a) => (
              <option key={a.id} value={`agent:${a.id}`}>
                {a.name}
                {a.id === project.masterAgentId ? ' (마스터)' : ''}
              </option>
            ))}
          </optgroup>
          <optgroup label="그룹 (리더가 받음)">
            {projectGroups.map((g) => (
              <option key={g.id} value={`group:${g.id}`}>
                {g.name}
              </option>
            ))}
          </optgroup>
        </select>
        {receiverHint && <span className={styles.hint}>{receiverHint}</span>}
        <button type="button" className={styles.toggle} onClick={onToggle} aria-expanded={expanded}>
          {expanded ? '기록 접기 ⌄' : `기록 ${messages.length} ⌃`}
        </button>
      </div>

      {expanded && (
        <div ref={logRef} className={styles.log}>
          {messages.length === 0 && <p className={shared.muted}>아직 보낸 명령이 없습니다.</p>}
          {messages.map((m) => {
            const rootId = m.rootExecutionId;
            return (
              <div key={m.id} className={`${styles.message} ${styles[m.role]}`}>
                <div className={styles.meta}>
                  <strong>{m.author}</strong>
                  {m.targetLabel && <span> → {m.targetLabel}</span>}
                </div>
                <div className={m.status === 'error' ? styles.error : undefined}>
                  {m.status === 'pending' ? <span className={styles.pending}>작업 중…</span> : m.text}
                </div>
                {rootId !== null && (
                  <ExecutionSummaryCard
                    executions={projectExecutions}
                    rootExecutionId={rootId}
                    onOpen={() => onOpenExecutions(rootId)}
                  />
                )}
              </div>
            );
          })}
        </div>
      )}

      <form onSubmit={onSubmit} className={styles.form}>
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={target ? '명령을 입력하고 Enter' : '먼저 대상을 선택하세요'}
          aria-label="명령 입력"
        />
        <button type="submit" disabled={!target || text.trim() === ''}>
          보내기
        </button>
      </form>
    </section>
  );
}
