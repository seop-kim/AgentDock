import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { formatCost, formatTokens } from '../../lib/executions';
import { featureNotice, useFeatureStore, type CostUsageBucket } from '../../lib/featureStore';
import { useAgentDockStore } from '../../store/AgentDockStore';
import shared from '../../styles/shared.module.css';
import styles from './Costs.module.css';

const RANGES: { key: string; label: string; days: number }[] = [
  { key: '7', label: '최근 7일', days: 7 },
  { key: '30', label: '최근 30일', days: 30 },
  { key: '90', label: '최근 90일', days: 90 },
];

function isoDay(date: Date): string {
  return date.toISOString().slice(0, 10);
}

/** 예산 대비 사용률(%). 예산이 없으면 null. */
function usagePercent(spent: number, budget: number): number | null {
  if (budget <= 0) return null;
  return (spent / budget) * 100;
}

/**
 * 비용·예산(/costs) — 기간별 사용량·프로젝트 예산·80% 경고.
 * 집계는 `GET /costs/usage`, 예산은 프로젝트별 `GET/PUT /projects/{id}/budget` 에서 온다.
 * API 가 아직 없으면 지금 읽어 둔 실행 목록의 실측 계측값으로 전체 기간 참고값을 보여 주고(모의 데이터 아님),
 * 기간 집계·예산 설정은 대기 문구로 남긴다.
 */
export default function Costs() {
  const { projects, executionList, loadExecutionList } = useAgentDockStore();
  const { costUsage, costBudgets, costStatus, loadCostUsage, loadCostBudgets, setProjectBudget } =
    useFeatureStore();

  const [rangeKey, setRangeKey] = useState('30');
  const [budgetDrafts, setBudgetDrafts] = useState<Record<number, string>>({});

  const loadRef = useRef(loadCostUsage);
  loadRef.current = loadCostUsage;

  const { from, to } = useMemo(() => {
    const days = RANGES.find((range) => range.key === rangeKey)?.days ?? 30;
    const end = new Date();
    const start = new Date(end.getTime() - (days - 1) * 24 * 60 * 60 * 1000);
    return { from: isoDay(start), to: isoDay(end) };
  }, [rangeKey]);

  useEffect(() => {
    loadExecutionList({ limit: 500 });
  }, []);

  useEffect(() => {
    loadRef.current(from, to);
  }, [from, to]);

  useEffect(() => {
    loadCostBudgets();
  }, []);

  const ready = costStatus === 'ready';
  const notice = featureNotice(costStatus);
  /** 실행 목록에서 만든 전체 기간 참고값(집계 API 가 아직 없을 때 대신 보여 준다). */
  const derived = useMemo(() => {
    const byProject: CostUsageBucket[] = projects
      .map((project) => {
        const mine = executionList.filter((execution) => execution.projectId === project.id);
        return {
          key: String(project.id),
          label: project.name,
          costUsd: mine.reduce((total, execution) => total + (execution.metrics?.costUsd ?? 0), 0),
          inputTokens: mine.reduce((total, execution) => total + (execution.metrics?.inputTokens ?? 0), 0),
          outputTokens: mine.reduce((total, execution) => total + (execution.metrics?.outputTokens ?? 0), 0),
          executions: mine.length,
        };
      })
      .filter((row) => row.executions > 0)
      .sort((left, right) => right.costUsd - left.costUsd);
    const costUsd = executionList.reduce((total, execution) => total + (execution.metrics?.costUsd ?? 0), 0);
    return { costUsd, byProject };
  }, [executionList, projects]);

  const totals = costUsage ?? {
    from,
    to,
    totalCostUsd: derived.costUsd,
    totalInputTokens: executionList.reduce((total, e) => total + (e.metrics?.inputTokens ?? 0), 0),
    totalOutputTokens: executionList.reduce((total, e) => total + (e.metrics?.outputTokens ?? 0), 0),
    totalExecutions: executionList.length,
    byDay: [] as CostUsageBucket[],
    byProject: derived.byProject,
  };
  const usedApi = costUsage !== null;

  const budgetOf = (projectId: number) => costBudgets.find((budget) => budget.projectId === projectId) ?? null;
  const spentOf = (projectId: number) => totals.byProject.find((row) => row.key === String(projectId))?.costUsd ?? 0;

  const submitBudget = (projectId: number) => {
    const raw = budgetDrafts[projectId];
    if (raw === undefined) return;
    const value = raw.trim() === '' ? null : Number(raw);
    if (value !== null && (Number.isNaN(value) || value < 0)) return;
    setProjectBudget(projectId, value);
    setBudgetDrafts((prev) => {
      const next = { ...prev };
      delete next[projectId];
      return next;
    });
  };
  return (
    <div>
      <div className={styles.header}>
        <h1>비용·예산</h1>
        <div className={styles.ranges}>
          {RANGES.map((range) => (
            <button
              key={range.key}
              type="button"
              className={`${styles.range} ${rangeKey === range.key ? styles.rangeOn : ''}`}
              onClick={() => setRangeKey(range.key)}
            >
              {range.label}
            </button>
          ))}
        </div>
      </div>
      <p className={shared.hint}>
        기간별 사용량을 기간 집계 API(GET /costs/usage)에서 읽고, 프로젝트 예산은 80% 에서 경고합니다. 비용·토큰은
        실행이 남긴 계측값을 그대로 더한 값이라 추정치가 아닙니다.
      </p>

      {notice !== null && <p className={shared.muted}>{notice}</p>}

      <section className={styles.totals}>
        <div className={styles.total}>
          <span className={shared.muted}>비용</span>
          <strong>{formatCost(totals.totalCostUsd)}</strong>
        </div>
        <div className={styles.total}>
          <span className={shared.muted}>입력 토큰</span>
          <strong>{formatTokens(totals.totalInputTokens)}</strong>
        </div>
        <div className={styles.total}>
          <span className={shared.muted}>출력 토큰</span>
          <strong>{formatTokens(totals.totalOutputTokens)}</strong>
        </div>
        <div className={styles.total}>
          <span className={shared.muted}>실행</span>
          <strong>{totals.totalExecutions}건</strong>
        </div>
      </section>
      <p className={shared.muted}>
        {usedApi
          ? `기간 ${totals.from} ~ ${totals.to} 의 집계입니다.`
          : '집계 API 가 열리기 전이라, 지금 읽어 둔 실행 목록(최근 500건) 기준 전체 참고값입니다.'}
      </p>
      {totals.byDay.length > 0 && (
        <>
          <h2>기간별 사용량</h2>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>기간</th>
                <th>실행</th>
                <th>입력</th>
                <th>출력</th>
                <th>비용</th>
              </tr>
            </thead>
            <tbody>
              {totals.byDay.map((bucket) => (
                <tr key={bucket.key}>
                  <td>{bucket.label}</td>
                  <td>{bucket.executions}</td>
                  <td>{formatTokens(bucket.inputTokens)}</td>
                  <td>{formatTokens(bucket.outputTokens)}</td>
                  <td>{formatCost(bucket.costUsd)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      <h2>프로젝트 예산</h2>      {projects.length === 0 ? (
        <p className={shared.muted}>프로젝트가 없습니다.</p>
      ) : (
        <table className={styles.table}>
          <thead>
            <tr>
              <th>프로젝트</th>
              <th>사용</th>
              <th>예산(USD)</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            {projects.map((project) => {
              const budget = budgetOf(project.id);
              const spent = spentOf(project.id);
              const pct = budget !== null ? usagePercent(spent, budget.budgetUsd) : null;
              const draft = budgetDrafts[project.id];
              return (
                <tr key={project.id}>
                  <td>
                    <Link to={`/projects/${project.id}`}>{project.name}</Link>
                  </td>
                  <td>{formatCost(spent)}</td>
                  <td className={styles.budgetCell}>
                    <input
                      type="number"
                      min="0"
                      step="1"
                      value={draft ?? (budget !== null ? String(budget.budgetUsd) : '')}
                      onChange={(event) =>
                        setBudgetDrafts((prev) => ({ ...prev, [project.id]: event.target.value }))
                      }
                      placeholder="예산 없음"
                      disabled={!ready}
                      aria-label={`${project.name} 예산(USD)`}
                    />
                    <button
                      type="button"
                      className={styles.ghost}
                      onClick={() => submitBudget(project.id)}
                      disabled={!ready}
                    >
                      저장
                    </button>
                  </td>
                  <td>
                    {pct === null ? (
                      <span className={shared.muted}>예산 없음</span>
                    ) : pct >= 100 ? (
                      <span className={`${styles.warn} ${styles.warnOver}`}>예산 초과 {Math.round(pct)}%</span>
                    ) : pct >= 80 ? (
                      <span className={styles.warn}>80% 경고 {Math.round(pct)}%</span>
                    ) : (
                      <span className={shared.muted}>{Math.round(pct)}% 사용</span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}