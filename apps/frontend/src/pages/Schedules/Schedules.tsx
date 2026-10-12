import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { PlayIcon, PlusIcon, TrashIcon } from '../../components/icons';
import { featureNotice, useFeatureStore, type Schedule, type ScheduleCadence } from '../../lib/featureStore';
import { useAgentDockStore } from '../../store/AgentDockStore';
import shared from '../../styles/shared.module.css';
import styles from './Schedules.module.css';

const CADENCE_LABEL: Record<ScheduleCadence, string> = {
  HOURLY: '매시간',
  DAILY: '매일',
  WEEKLY: '매주',
  CRON: '크론식',
};
const CADENCES: ScheduleCadence[] = ['HOURLY', 'DAILY', 'WEEKLY', 'CRON'];

function formatWhen(value: string | null): string {
  if (value === null || value === '') return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('ko-KR');
}

/**
 * 예약(/schedules) — 주기 목록·생성·즉시 실행.
 * 데이터는 useFeatureStore(스토어 확장 슬라이스)로만 읽고, API 가 아직 없으면 대기 문구를 남긴다.
 */
export default function Schedules() {
  const { projects, agents, groups } = useAgentDockStore();
  const {
    schedules,
    schedulesStatus,
    loadSchedules,
    createSchedule,
    deleteSchedule,
    setScheduleEnabled,
    runScheduleNow,
  } = useFeatureStore();

  const [formOpen, setFormOpen] = useState(false);
  const [projectId, setProjectId] = useState('');
  const [name, setName] = useState('');
  const [cadence, setCadence] = useState<ScheduleCadence>('DAILY');
  const [cron, setCron] = useState('');
  const [prompt, setPrompt] = useState('');
  const [target, setTarget] = useState('');

  useEffect(() => {
    loadSchedules();
  }, []);
  const ready = schedulesStatus === 'ready';
  const notice = featureNotice(schedulesStatus);
  const currentProjectId = projectId === '' ? (projects[0]?.id ?? null) : Number(projectId);

  const targetOptions = useMemo(() => {
    if (currentProjectId === null) return [];
    return [
      ...agents
        .filter((agent) => agent.projectId === currentProjectId)
        .map((agent) => ({ value: `agent:${agent.id}`, label: `에이전트 · ${agent.name}` })),
      ...groups
        .filter((group) => group.projectId === currentProjectId)
        .map((group) => ({ value: `group:${group.id}`, label: `팀 · ${group.name}` })),
    ];
  }, [agents, groups, currentProjectId]);

  const canSubmit =
    name.trim() !== '' &&
    prompt.trim() !== '' &&
    (cadence !== 'CRON' || cron.trim() !== '') &&
    currentProjectId !== null;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!canSubmit || currentProjectId === null) return;
    const [kind, rawId] = target.split(':');
    createSchedule({
      projectId: currentProjectId,
      name: name.trim(),
      cadence,
      cron: cadence === 'CRON' ? cron.trim() : null,
      prompt: prompt.trim(),
      targetAgentId: kind === 'agent' && rawId ? Number(rawId) : null,
      groupId: kind === 'group' && rawId ? Number(rawId) : null,
    });
    setFormOpen(false);
    setName('');
    setCron('');
    setPrompt('');
    setTarget('');
  };

  const projectNameOf = (id: number) => projects.find((project) => project.id === id)?.name ?? `프로젝트 #${id}`;
  const targetLabelOf = (schedule: Schedule) =>
    schedule.groupId !== null
      ? `팀 · ${groups.find((group) => group.id === schedule.groupId)?.name ?? schedule.groupId}`
      : schedule.targetAgentId !== null
        ? `에이전트 · ${agents.find((agent) => agent.id === schedule.targetAgentId)?.name ?? schedule.targetAgentId}`
        : '프로젝트 마스터';
  return (
    <div>
      <div className={styles.header}>
        <h1>예약</h1>
        <button
          type="button"
          className={styles.primary}
          onClick={() => setFormOpen(true)}
          disabled={!ready || projects.length === 0}
        >
          <PlusIcon size={16} /> 새 예약
        </button>
      </div>
      <p className={shared.hint}>
        주기를 정해 두면 스케줄러가 그 시각에 명령을 실행합니다. 즉시 실행은 지금 한 번 돌려 보고, 실제 실행 기록은
        프로젝트 상세의 실행 트리에서 봅니다.
      </p>

      {notice !== null && <p className={shared.muted}>{notice}</p>}
      {formOpen && (
        <form className={styles.form} onSubmit={submit}>
          <div className={styles.grid}>
            <label className={styles.field}>
              <span>프로젝트</span>
              <select
                value={projectId === '' ? String(projects[0]?.id ?? '') : projectId}
                onChange={(event) => {
                  setProjectId(event.target.value);
                  setTarget('');
                }}
                required
              >
                {projects.map((project) => (
                  <option key={project.id} value={project.id}>
                    {project.name}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              <span>이름</span>
              <input value={name} onChange={(event) => setName(event.target.value)} placeholder="야간 테스트 정리" required />
            </label>
            <label className={styles.field}>
              <span>주기</span>
              <select value={cadence} onChange={(event) => setCadence(event.target.value as ScheduleCadence)}>
                {CADENCES.map((value) => (
                  <option key={value} value={value}>
                    {CADENCE_LABEL[value]}
                  </option>
                ))}
              </select>
            </label>
            {cadence === 'CRON' && (
              <label className={styles.field}>
                <span>크론식</span>
                <input value={cron} onChange={(event) => setCron(event.target.value)} placeholder="0 3 * * *" required />
              </label>
            )}
            <label className={styles.field}>
              <span>보낼 대상</span>
              <select value={target} onChange={(event) => setTarget(event.target.value)}>
                <option value="">프로젝트 마스터</option>
                {targetOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          </div>          <label className={styles.field}>
            <span>지시(프롬프트)</span>
            <textarea
              value={prompt}
              onChange={(event) => setPrompt(event.target.value)}
              rows={3}
              placeholder="매일 밤 실패한 테스트를 정리해줘"
              required
            />
          </label>
          <div className={shared.actions}>
            <button type="submit" disabled={!canSubmit}>
              예약 만들기
            </button>
            <button type="button" className={styles.ghost} onClick={() => setFormOpen(false)}>
              취소
            </button>
          </div>
        </form>
      )}
      {schedules.length === 0 ? (
        <p className={shared.muted}>
          {ready ? '아직 예약이 없습니다. "새 예약"으로 주기를 만들어 보세요.' : '예약 목록을 기다리는 중입니다.'}
        </p>
      ) : (
            <ul className={styles.list}>
              {schedules.map((schedule) => (
                <li key={schedule.id} className={styles.row}>
                  <div className={styles.rowMain}>
                    <span className={styles.rowName}>{schedule.name}</span>
                    <span className={styles.badge}>
                      {CADENCE_LABEL[schedule.cadence]}
                      {schedule.cron ? ` · ${schedule.cron}` : ''}
                    </span>
                    <span className={`${styles.badge} ${schedule.enabled ? styles.badgeOn : ''}`}>
                      {schedule.enabled ? '켜짐' : '꺼짐'}
                    </span>
                    <span className={shared.muted}>
                      {projectNameOf(schedule.projectId)} · {targetLabelOf(schedule)}
                    </span>
                  </div>
                  <p className={styles.meta}>
                    다음 {formatWhen(schedule.nextRunAt)} · 지난 {formatWhen(schedule.lastRunAt)}
                    {schedule.lastExecutionId !== null ? ` · 실행 #${schedule.lastExecutionId}` : ''}
                  </p>
                  <div className={styles.rowActions}>
                    <button
                      type="button"
                      className={styles.ghost}
                      onClick={() => runScheduleNow(schedule.id)}
                      disabled={!ready}
                      title="지금 한 번 실행"
                    >
                      <PlayIcon size={15} /> 즉시 실행
                    </button>
                    <button
                      type="button"
                      className={styles.ghost}
                      onClick={() => setScheduleEnabled(schedule.id, !schedule.enabled)}
                      disabled={!ready}
                    >
                      {schedule.enabled ? '끄기' : '켜기'}
                    </button>
                    <button
                      type="button"
                      className={styles.danger}
                      onClick={() => deleteSchedule(schedule.id)}
                      disabled={!ready}
                    >
                      <TrashIcon size={15} /> 삭제
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
    </div>
  );
}