import { useAgentDockStore } from '../store/AgentDockStore';

/*
 * C/D/E/G/H 화면이 읽는 스토어 확장 슬라이스의 얇은 뷰.
 *
 * api.ts · store/AgentDockStore.tsx · types.ts 는 데이터 담당(19)이 채운다(18 은 편집하지 않는다).
 * 19 가 이 슬라이스를 스토어에 넣으면 화면이 곧바로 켜지고, 아직 없으면 'unsupported' 로 내려가
 * 화면에 대기·미지원 문구가 뜬다(목업/모의 데이터를 넣지 않는다). 계약은 .agentdock/share/프론트화면18.md.
 */

export type FeatureStatus = 'idle' | 'loading' | 'ready' | 'unsupported' | 'error';

export interface ExecutionDiffFile {
  path: string;
  status: string;
  diff: string;
}
export interface ExecutionDiff {
  executionId: number;
  files: ExecutionDiffFile[];
}

export type ScheduleCadence = 'HOURLY' | 'DAILY' | 'WEEKLY' | 'CRON';
export interface Schedule {
  id: number;
  projectId: number;
  name: string;
  cadence: ScheduleCadence;
  cron: string | null;
  prompt: string;
  targetAgentId: number | null;
  groupId: number | null;
  enabled: boolean;
  nextRunAt: string | null;
  lastRunAt: string | null;
  lastExecutionId: number | null;
}
export interface NewSchedule {
  projectId: number;
  name: string;
  cadence: ScheduleCadence;
  cron: string | null;
  prompt: string;
  targetAgentId: number | null;
  groupId: number | null;
}
export interface MergePolicy {
  autoMerge: boolean;
  autoCleanupWorktree: boolean;
  autoCleanupBranch: boolean;
  requireCleanTarget: boolean;
}

export interface CostUsageBucket {
  key: string;
  label: string;
  costUsd: number;
  inputTokens: number;
  outputTokens: number;
  executions: number;
}
export interface CostUsage {
  from: string;
  to: string;
  totalCostUsd: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalExecutions: number;
  byDay: CostUsageBucket[];
  byProject: CostUsageBucket[];
}
export interface ProjectBudget {
  projectId: number;
  projectName: string;
  budgetUsd: number;
  spentUsd: number;
}

export type NotificationChannelKind = 'WEBHOOK' | 'SLACK' | 'EMAIL';
export interface NotificationChannel {
  id: number;
  kind: NotificationChannelKind;
  name: string;
  target: string;
  enabled: boolean;
}
export interface NotificationRule {
  id: number;
  channelId: number;
  event: string;
  enabled: boolean;
}
export interface NewNotificationChannel {
  kind: NotificationChannelKind;
  name: string;
  target: string;
  enabled: boolean;
}
export interface NewNotificationRule {
  channelId: number;
  event: string;
  enabled: boolean;
}
/** 데이터 담당(19)이 스토어에 넣어 준 슬라이스(확정 이름). 아직 없으면 화면은 미지원으로 본다. */
interface FeatureRaw {
  executionDiffs: Record<number, ExecutionDiff | null>;
  diffStatus: Record<number, FeatureStatus>;
  loadExecutionDiff: (executionId: number) => void;

  schedules: Schedule[];
  schedulesStatus: FeatureStatus;
  loadSchedules: () => void;
  createSchedule: (input: NewSchedule) => void;
  deleteSchedule: (id: number) => void;
  setScheduleEnabled: (id: number, enabled: boolean) => void;
  runScheduleNow: (id: number) => void;

  mergePolicy: MergePolicy | null;
  mergePolicyStatus: FeatureStatus;
  loadMergePolicy: () => void;
  saveMergePolicy: (policy: MergePolicy) => void;

  costUsage: CostUsage | null;
  costBudgets: ProjectBudget[];
  costStatus: FeatureStatus;
  loadCostUsage: (from: string, to: string) => void;
  loadCostBudgets: () => void;
  setProjectBudget: (projectId: number, budgetUsd: number | null) => void;

  notificationChannels: NotificationChannel[];
  notificationRules: NotificationRule[];
  notificationStatus: FeatureStatus;
  loadNotifications: () => void;
  createChannel: (input: NewNotificationChannel) => void;
  updateChannel: (id: number, input: NewNotificationChannel) => void;
  deleteChannel: (id: number) => void;
  testChannel: (id: number) => void;
  createRule: (input: NewNotificationRule) => void;
  deleteRule: (id: number) => void;
  setRuleEnabled: (id: number, enabled: boolean) => void;
}

export type FeatureStore = Omit<FeatureRaw, 'executionDiffs' | 'diffStatus'> & {
  diffOf: (executionId: number) => ExecutionDiff | null;
  diffStatusOf: (executionId: number) => FeatureStatus;
};

/**
 * 기능 상태를 화면에 보여 줄 안내 문구로 바꾼다.
 * 값이 없거나(idle) 정상(ready)이면 안내하지 않고 null 을 준다 —
 * 화면은 대기·미지원·오류일 때만 문구를 띄운다.
 */
export function featureNotice(status: FeatureStatus): string | null {
  switch (status) {
    case 'loading':
      return '불러오는 중입니다…';
    case 'unsupported':
      return '이 기능의 API 가 아직 서버에 없습니다. 서버에 준비되면 자동으로 켜집니다.';
    case 'error':
      return '정보를 읽지 못했습니다. 잠시 후 다시 시도하세요.';
    default:
      return null;
  }
}

const noop = () => {};
/**
 * 스토어 확장 슬라이스를 안전하게 읽는다.
 * 값이 아직 없는 필드는 빈 배열/null/미지원 상태로 내려 주고, 액션이 없으면 noop 을 준다 —
 * 화면은 "값이 없음"과 "API 미지원"을 구분하지 않고 대기·미지원 문구로 표시한다.
 */
export function useFeatureStore(): FeatureStore {
  const store = useAgentDockStore() as unknown as Partial<FeatureRaw>;
  return {
    diffOf: (executionId) => store.executionDiffs?.[executionId] ?? null,
    diffStatusOf: (executionId) => store.diffStatus?.[executionId] ?? 'unsupported',
    loadExecutionDiff: store.loadExecutionDiff ?? noop,

    schedules: store.schedules ?? [],
    schedulesStatus: store.schedulesStatus ?? 'unsupported',
    loadSchedules: store.loadSchedules ?? noop,
    createSchedule: store.createSchedule ?? noop,
    deleteSchedule: store.deleteSchedule ?? noop,
    setScheduleEnabled: store.setScheduleEnabled ?? noop,
    runScheduleNow: store.runScheduleNow ?? noop,

    mergePolicy: store.mergePolicy ?? null,
    mergePolicyStatus: store.mergePolicyStatus ?? 'unsupported',
    loadMergePolicy: store.loadMergePolicy ?? noop,
    saveMergePolicy: store.saveMergePolicy ?? noop,

    costUsage: store.costUsage ?? null,
    costBudgets: store.costBudgets ?? [],
    costStatus: store.costStatus ?? 'unsupported',
    loadCostUsage: store.loadCostUsage ?? noop,
    loadCostBudgets: store.loadCostBudgets ?? noop,
    setProjectBudget: store.setProjectBudget ?? noop,

    notificationChannels: store.notificationChannels ?? [],
    notificationRules: store.notificationRules ?? [],
    notificationStatus: store.notificationStatus ?? 'unsupported',
    loadNotifications: store.loadNotifications ?? noop,
    createChannel: store.createChannel ?? noop,
    updateChannel: store.updateChannel ?? noop,
    deleteChannel: store.deleteChannel ?? noop,
    testChannel: store.testChannel ?? noop,
    createRule: store.createRule ?? noop,
    deleteRule: store.deleteRule ?? noop,
    setRuleEnabled: store.setRuleEnabled ?? noop,
  };
}