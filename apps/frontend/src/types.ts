export interface Capabilities {
  models: string[];
  modes: string[];
  notes: string | null;
  install: string[];
  installRequire: string | null;
  installPrerequisite: string[];
}

export interface AiProvider {
  id: number;
  key: string;
  name: string;
  enabled: boolean;
  capabilities: Capabilities;
}

/**
 * 화면·스토어가 쓰는 런타임 타입 이름. `AiProvider` 와 같은 타입이다
 * (스토어가 `Provider` 라는 이름으로 import 한다).
 */
export type Provider = AiProvider;

export interface CliStatus {
  runnable: boolean;
  resolvedPath: string | null;
  version: string | null;
  detail: string | null;
}

export interface Workspace {
  id: number;
  name: string;
  path: string;
}

export interface ProjectWorkspaceInfo {
  workspaceId: number;
  isDefault: boolean;
}

export interface Project {
  id: number;
  name: string;
  workspaces: ProjectWorkspaceInfo[];
  /** 프로젝트 마스터 에이전트(최상위 리더). 프로젝트에 반드시 하나 있어야 한다. */
  masterAgentId: number | null;
  /** 마스터 프롬프트. 프롬프트 계층(마스터 → 그룹 → 에이전트)의 맨 위. */
  masterPrompt: string;
}

export interface AgentRole {
  id: number;
  name: string;
}

export interface PermissionProfile {
  id: number;
  name: string;
}

export interface Agent {
  id: number;
  projectId: number;
  name: string;
  roleId: number;
  permissionProfileId: number;
  providerId: number;
  persona: string;
  model: string;
  mode: string;
  /** 구성도에 놓였는지. false 면 구성도에는 없고 에이전트 목록에만 있다. */
  placed: boolean;
  /** 백엔드 파생값: 지금 새 실행을 만들 수 있는지(런타임 삭제/꺼짐, 폴더 미확인). */
  available: boolean;
  /** 실행할 수 없는 사유(백엔드 `AgentAvailability.evaluate` 값). 실행 가능하면 null. */
  unavailableReason:
    | 'PROVIDER_DELETED'
    | 'RUNTIME_DISABLED'
    | 'CONNECTION_NOT_STARTED'
    | 'CONNECTION_NOT_CONNECTED'
    | null;
}

export interface AgentGroup {
  id: number;
  projectId: number;
  name: string;
  leaderAgentId: number | null;
  /** 한 에이전트는 여러 그룹에 속할 수 있다. */
  memberIds: number[];
  /** 그룹 프롬프트. 마스터 프롬프트 아래, 에이전트 프롬프트 위. */
  prompt: string;
  /** 그룹 프롬프트와 별개인 **공유 노트**. 그룹 안 실행들이 함께 보는 맥락이라 트리가 끝날 때 자동으로 자란다. */
  sharedNote: string;
}

export type TaskStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';

export interface Task {
  id: number;
  projectId: number;
  title: string;
  status: TaskStatus;
  /** 이 Task 를 받은 에이전트(그룹에 보내면 리더). 담당이 없으면 null. */
  agentId: number | null;
  /**
   * 이 Task(명령)에 함께 보낸 첨부 파일. 서버 `TaskResponse.attachments` 에서 온다 —
   * 채팅을 다시 만들 때(`rebuildChats`) 내 말풍선의 첨부가 사라지지 않게 복원하는 데 쓴다.
   */
  attachments: AttachedFile[];
}

/** 실행 단위의 상태. 부모는 자식이 도는 동안 WAITING_CHILD 로, 사람에게 물으면 WAITING_INPUT 으로 멈춘다. */
export type ExecutionStatus = 'QUEUED' | 'RUNNING' | 'WAITING_CHILD' | 'WAITING_INPUT' | 'DONE' | 'FAILED' | 'CANCELLED';

/**
 * 실행 출력의 **마지막 줄에 강제하는 계약**. 판단이 필요한 마스터·리더만 쓴다.
 * 계약은 프롬프트로 강제하고, 결과 텍스트에서 JSON 을 찾아 파싱한다(코드블록이 섞여도 동작).
 */
export type ExecutionDecision =
  | { action: 'delegate'; targetAgentId: number; prompt: string }
  | { action: 'ask'; question: string }
  | { action: 'done'; summary: string };

/** CLI 의 `--output-format json` 이 그대로 돌려주는 값(usage / total_cost_usd / duration_ms). */
export interface ExecutionMetrics {
  inputTokens: number;
  outputTokens: number;
  costUsd: number;
  durationMs: number;
}

/** 특정 Agent 가 실제로 수행한 한 번의 실행. 마스터 실행이 트리의 루트다. */
export interface Execution {
  id: number;
  projectId: number;
  agentId: number;
  /** 이 실행을 만든 부모 실행. 루트(마스터) 실행은 null. */
  parentExecutionId: number | null;
  /** 이 실행이 속한 트리의 루트 실행 id(루트 자신이면 자기 id). 실행 목록을 트리로 묶을 때 쓴다. */
  rootExecutionId: number | null;
  status: ExecutionStatus;
  /** 이 실행이 받은 지시. */
  prompt: string;
  /** 이 실행이 낸 판단 계약. 위임한 실행은 위임 대상이, 직접 처리한 실행은 완료 요약이 남는다. */
  decision: ExecutionDecision | null;
  /** 끝난 뒤 위로 넘기는 최소 Handoff. 원문 전체가 아니라 요약 + 변경 파일이다. */
  handoff: { summary: string; changedFiles: string[] } | null;
  /** 실행이 끝났을 때 채워진다. */
  metrics: ExecutionMetrics | null;
  /** 세션 재개(`--resume`)에 쓰는 실행 세션 id. */
  sessionId: string;
  /**
   * 이 실행 트리가 도는 git worktree 경로(루트가 만들고 자식이 물려받는다).
   * 워크스페이스가 git 저장소가 아니거나 만들지 못했으면 null(그때는 워크스페이스에서 실행).
   */
  worktreePath: string | null;
  /** worktree 의 브랜치 이름(`agentdock/exec-<루트실행id>`). 격리하지 못하면 null. */
  worktreeBranch: string | null;
  /** 트리 브랜치에 남긴 커밋의 짧은 sha. 커밋할 것이 없었으면 null(트리 루트에만 채워진다). */
  resultCommit: string | null;
  /** 트리 결과를 메인 저장소로 되돌린 결과. 되돌릴 것이 없으면 null(트리 루트에만 채워진다). */
  mergeStatus: MergeStatus | null;
  /** 자동 병합하지 못한 사유(MANUAL 일 때). */
  mergeDetail: string | null;
  /** 실패 사유(CLI 종료 코드 등). "판단 중…"으로 남지 않게 화면이 이 값을 보여 준다. */
  errorMessage: string | null;
  /** 트리가 바꾼 파일 목록(git `--name-status`). 커밋·병합하지 못했으면 빈 배열. */
  changedFiles: ChangedFile[];
  /** 판단 실행이 사람에게 물은 질문(계약 `ask`). 답을 기다리는 동안 채워져 있다. */
  question: string | null;
  /** 그 질문에 함께 온 보기(`options`). 화면이 답을 버튼으로 고르게 한다. 없으면 빈 배열. */
  options: string[];
  /** 그 질문에 대한 사람의 답(`POST /executions/{id}/answer`). */
  answer: string | null;
}

/** 트리 결과를 메인 저장소로 되돌린 결과. */
export type MergeStatus = 'MERGED' | 'MANUAL' | 'NONE';

/** 트리가 바꾼 파일 하나. status 는 git 변경 코드(A 추가 / M 수정 / D 삭제 …). */
export interface ChangedFile {
  status: string;
  path: string;
}

/** 명령에 첨부한 파일. 경로는 그 워크스페이스 폴더 기준의 상대 경로다. */
export interface AttachedFile {
  /**
   * 백엔드 첨부 레코드 id. 드래그드롭 업로드(`uploadAttachments`)로 붙인 파일에만 실제 값이 있고,
   * 폴더에서 고른 파일처럼 레코드가 없으면 `0`(백엔드는 없는 id 를 그냥 건너뛴다).
   * 스토어의 `sendCommand` 가 `attachments.map((a) => a.id)` 로 `number[]` 를 만들기 때문에 optional 로 둘 수 없다.
   */
  id: number;
  workspaceId: number;
  /** 프로젝트 폴더 안에 **저장된** 경로. 밖에서 끌어온 파일은 복사본이라 이름 앞에 id 가 붙는다. */
  path: string;
  /** 원래 파일 이름(화면 표시용). 저장 경로와 다를 수 있다. */
  name: string;
}

/** 채팅 명령의 대상. 그룹에 보내면 그 그룹의 리더가 받는다. */
export type ChatTarget = { kind: 'agent'; id: number } | { kind: 'group'; id: number };

export interface ChatMessage {
  id: number;
  projectId: number;
  role: 'user' | 'agent' | 'system';
  /** 보낸 사람 표시: 나 / 에이전트 이름 / 시스템 */
  author: string;
  text: string;
  /** 사용자 메시지의 받는 쪽 표시(예: "Backend Team → 리더 Backend Dev A") */
  targetLabel?: string;
  /** 에이전트 응답이 아직 오는 중이면 pending */
  status: 'pending' | 'done' | 'error';
  /** 이 응답으로 만들어진 실행 트리의 루트 실행. 명령이 아니면 null. */
  rootExecutionId: number | null;
  /** 명령에 함께 보낸 첨부 파일. 없으면 빈 배열. */
  attachments: AttachedFile[];
}

// ── 조각별 상태 ─────────────────────────────────────────────────────────────────

/**
 * 데이터 조각을 언제·어떻게 읽었는지. 화면이 **빈 목록과 구분**해
 * '아직 안 읽음(idle) / 읽는 중(loading) / 정상(ready) / 서버 미지원(unsupported) / 오류(error)' 을 표시한다.
 * 백엔드가 아직 안 만든 API 는 목업으로 때우지 않고 `unsupported` 로 남겨 화면이 대기·미지원을 보여 준다.
 */
export type FeatureStatus = 'idle' | 'loading' | 'ready' | 'unsupported' | 'error';

// ── C. 실행 변경사항 diff (`GET /executions/{id}/diff`) ─────────────────────────────

/** diff 창이 보여 주는 파일 하나 — git 변경 코드와 파일별 unified diff. */
export interface ExecutionDiffFile {
  path: string;
  /** git 변경 코드(A 추가 / M 수정 / D 삭제 / R 이름변경 …). */
  status: string;
  /** 파일별 unified diff 본문. */
  diff: string;
}

/** 트리 루트 실행의 변경 파일별 unified diff(트리 루트 행에서 창으로 연다). */
export interface ExecutionDiff {
  /** 트리 루트 실행 id. */
  executionId: number;
  files: ExecutionDiffFile[];
}

// ── D. 예약(반복) 실행 (`/schedules`) ───────────────────────────────────────────────

export type ScheduleCadence = 'HOURLY' | 'DAILY' | 'WEEKLY' | 'CRON';

/** 예약 실행 하나 — 주기(cadence/cron)와 실행할 명령, 받을 대상, 다음/마지막 실행 시각. */
export interface Schedule {
  id: number;
  projectId: number;
  name: string;
  cadence: ScheduleCadence;
  /** cadence 가 CRON 일 때의 크론식(다른 주기는 null). */
  cron: string | null;
  prompt: string;
  targetAgentId: number | null;
  groupId: number | null;
  enabled: boolean;
  nextRunAt: string | null;
  lastRunAt: string | null;
  lastExecutionId: number | null;
}

/** 예약 생성 요청 본문. */
export interface NewSchedule {
  projectId: number;
  name: string;
  cadence: ScheduleCadence;
  cron: string | null;
  prompt: string;
  targetAgentId: number | null;
  groupId: number | null;
}

// ── E. 병합 정책 (`GET/PUT /settings/merge-policy`) ────────────────────────────────

/** 워크트리 자동 정리·병합 정책. 트리가 끝난 뒤 무엇을 자동으로 할지 정한다. */
export interface MergePolicy {
  /** 메인 저장소가 깨끗하면 자동으로 병합한다. */
  autoMerge: boolean;
  /** 병합 성공(MERGED) 후 워크트리를 자동 정리한다. */
  autoCleanupWorktree: boolean;
  /** 정리할 때 전용 브랜치도 함께 삭제한다. */
  autoCleanupBranch: boolean;
  /** 병합 전에 대상(메인) 저장소가 깨끗해야 한다. */
  requireCleanTarget: boolean;
}

// ── G. 비용·예산 (`/costs`) ────────────────────────────────────────────────────────

/** 기간 집계의 한 버킷(날짜별/프로젝트별). */
export interface CostUsageBucket {
  /** 버킷 키(날짜 또는 프로젝트 id). */
  key: string;
  label: string;
  costUsd: number;
  inputTokens: number;
  outputTokens: number;
  executions: number;
}

/** 기간별 사용량 집계 응답. CLI 가 돌려준 실측 계측값만 더한다(추정 금지). */
export interface CostUsage {
  from: string;
  to: string;
  totalCostUsd: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalExecutions: number;
  /** 날짜별 버킷. */
  byDay: CostUsageBucket[];
  /** 프로젝트별 버킷. */
  byProject: CostUsageBucket[];
}

/** 프로젝트 예산과 사용 현황(80% 경고는 화면이 spent/budget 비율로 그린다). */
export interface ProjectBudget {
  projectId: number;
  projectName: string;
  budgetUsd: number;
  spentUsd: number;
}

// ── H. 알림 채널·규칙 (`/notifications`) ───────────────────────────────────────────

export type NotificationChannelKind = 'WEBHOOK' | 'SLACK' | 'EMAIL';

/** 알림을 보낼 채널 하나(웹훅·Slack·메일). */
export interface NotificationChannel {
  id: number;
  kind: NotificationChannelKind;
  name: string;
  /** 발송 대상(웹훅 URL·Slack 웹훅 URL·메일 주소). 비밀값은 서버가 가려서 준다. */
  target: string;
  enabled: boolean;
}

/** 어떤 이벤트를 어느 채널로 보낼지 정하는 규칙. */
export interface NotificationRule {
  id: number;
  channelId: number;
  event: string;
  enabled: boolean;
}

/** 알림 채널 생성·수정 요청 본문. */
export interface NewNotificationChannel {
  kind: NotificationChannelKind;
  name: string;
  target: string;
  enabled: boolean;
}

/** 알림 규칙 생성·수정 요청 본문. */
export interface NewNotificationRule {
  channelId: number;
  event: string;
  enabled: boolean;
}
