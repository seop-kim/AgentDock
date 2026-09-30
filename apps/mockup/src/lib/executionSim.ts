import type { AgentGroup, AttachedFile, ExecutionDecision, ExecutionMetrics, ExecutionStatus } from '../types';

/* ---------------------------------------------------------------------------
 * 실행 흐름 모의.
 *   - 라우팅은 **규칙 먼저**(Rule Router). 규칙으로 정해지면 LLM 을 부르지 않는다.
 *   - 판단이 필요한 실행(마스터·리더)은 출력 끝에 계약(delegate | done)을 낸다. 실제 구현에서는
 *     CLI 의 `--json-schema` 로 형식을 강제한다.
 *   - 자식이 도는 동안 부모는 WAITING_CHILD 로 멈췄다가, 자식이 모두 끝나면 다시 RUNNING 이 되어
 *     결과를 모으고 done 을 낸다. 화면의 상태 전이가 이 흐름을 그대로 따른다.
 *   - 지표(토큰·비용·시간)는 CLI `--output-format json` 이 돌려주는 값 자리에 넣는 **모의 값**이다.
 *     단가는 실측 1회(입력 43,830 / 출력 4 / $0.263)에서 역산했다: 입력 $6/M, 출력 $30/M.
 * ------------------------------------------------------------------------- */

/** 팀별 라우팅 키워드. 이 규칙으로 정해지면 마스터 LLM 을 부르지 않는다. */
const TEAM_KEYWORDS: Record<string, string[]> = {
  'Backend Team': ['api', '서버', '백엔드', 'db', '쿼리', 'n+1', '엔드포인트', '로직'],
  'Frontend Team': ['화면', 'ui', '컴포넌트', '버튼', '스타일', '프론트', '레이아웃', '문구'],
  'QA Team': ['테스트', '버그', '재현', '검증', '회귀'],
  'DevOps Team': ['배포', 'ci', '도커', '인프라', '빌드'],
  'Docs Team': ['문서', 'readme', '가이드', '주석'],
};

/** 첨부한 파일 경로로 팀을 정하는 규칙. 확장자와 폴더 이름만 본다(내용은 읽지 않는다). */
const FILE_ROUTES: Record<string, string[]> = {
  'Backend Team': ['.java', '/api/', 'controller', 'service', 'repository'],
  'Frontend Team': ['.tsx', '.jsx', '.css', 'components/', 'pages/'],
  'QA Team': ['/test/', 'test.', 'spec.'],
  'DevOps Team': ['build.gradle', 'pom.xml', 'package.json', '.yml', '.yaml', 'dockerfile'],
  'Docs Team': ['.md', 'docs/'],
};

/** 팀별 결과 요약(모의). 실제로는 그 에이전트가 Handoff 로 돌려주는 값이다. */
const TEAM_SUMMARY: Record<string, string> = {
  'Backend Team': '원인을 확인하고 쿼리를 정리했습니다.',
  'Frontend Team': '컴포넌트를 고치고 좁은 화면까지 확인했습니다.',
  'QA Team': '재현 절차를 만들고 경계값을 확인했습니다.',
  'DevOps Team': '배포 설정을 고치고 되돌리는 방법을 적었습니다.',
  'Docs Team': '문서를 고치고 예시를 추가했습니다.',
};

const DEFAULT_SUMMARY = '맡은 범위를 확인하고 정리했습니다.';

/** 팀별로 나올 법한 변경 파일(모의). Handoff 의 changedFiles 자리에 넣는다. */
const TEAM_FILES: Record<string, string[]> = {
  'Backend Team': ['src/main/java/com/shop/api/InventoryService.java'],
  'Frontend Team': ['src/components/InventoryTable.tsx'],
  'QA Team': ['src/test/java/com/shop/api/InventoryServiceTest.java'],
  'DevOps Team': ['.github/workflows/deploy.yml'],
  'Docs Team': ['docs/inventory.md'],
};

/** 재생용 시간(ms). 실제 도구는 실행마다 수십 초가 걸리지만, 여기서는 눈으로 보라고 압축한다. */
const DECIDE_MS = 1200;
const WORK_MS = 1700;
const FINALIZE_MS = 1000;

export interface PlanEvent {
  at: number;
  key: string;
  status: ExecutionStatus;
  decision?: ExecutionDecision;
  handoff?: { summary: string; changedFiles: string[] };
  metrics?: ExecutionMetrics;
}

export interface PlannedExecution {
  key: string;
  agentId: number;
  parentKey: string | null;
  prompt: string;
  sessionId: string;
}

export interface ExecutionPlan {
  /** 부모가 자식보다 먼저 오는 순서. 첫 항목이 루트다. */
  nodes: PlannedExecution[];
  /** 이 시각(ms)에 이 실행을 이 상태로 바꾼다. */
  timeline: PlanEvent[];
  /** 마스터가 나눠 맡긴 팀 이름. 직접 처리면 빈 배열이다. */
  teamNames: string[];
  executions: number;
  delegations: number;
  costUsd: number;
  /** 루트 실행이 끝나기까지 걸린 시간. 자식들이 병렬로 도는 것이 반영된 값이다. */
  durationMs: number;
}

/**
 * 규칙으로 팀을 정한다. 요청 문장의 키워드로 먼저 보고, 첨부한 파일 경로로도 본다.
 * 어느 팀도 걸리지 않으면 마스터가 직접 처리한다는 뜻이다.
 */
export function routeTeams(text: string, groups: AgentGroup[], attachments: AttachedFile[]): AgentGroup[] {
  const lower = text.toLowerCase();
  const paths = attachments.map((file) => file.path.toLowerCase());
  return groups.filter((group) => {
    if ((TEAM_KEYWORDS[group.name] ?? []).some((keyword) => lower.includes(keyword))) return true;
    return paths.some((path) => (FILE_ROUTES[group.name] ?? []).some((hint) => path.includes(hint)));
  });
}

/** 실행 트리 하나(마스터 실행을 루트로 한)를 미리 계산한다. 화면은 이 계획대로 상태를 바꾼다. */
export function buildExecutionPlan(input: {
  text: string;
  rootAgentId: number;
  /** 마스터가 낸 명령인지. 마스터만 팀으로 나눠 맡긴다. */
  fromMaster: boolean;
  groups: AgentGroup[];
  /** 함께 보낸 파일. 라우팅과 프롬프트에 쓰인다. */
  attachments: AttachedFile[];
}): ExecutionPlan {
  const { text, rootAgentId, fromMaster, groups, attachments } = input;
  const root: Draft = {
    key: 'e0',
    agentId: rootAgentId,
    prompt: withAttachments(text, attachments),
    teamName: null,
    startAt: 0,
    children: [],
    metrics: null,
  };

  const teams = fromMaster ? routeTeams(text, groups, attachments) : [];
  teams.forEach((group, index) => {
    const leaderId = group.leaderAgentId;
    if (leaderId === null) return;
    const instruction = `"${short(text)}" 를 ${group.name} 범위에서 처리`;
    const leader: Draft = {
      key: `e${index + 1}`,
      agentId: leaderId,
      prompt: withAttachments(instruction, attachments),
      teamName: group.name,
      startAt: 0,
      children: [],
      metrics: null,
    };
    root.children.push(leader);
    addSubDelegation(leader, group);
  });

  // 그룹에 직접 보낸 명령도 리더가 판단한다: 팀이 크면 하위에 넘긴다.
  if (!fromMaster) {
    const group = groups.find((g) => g.leaderAgentId === rootAgentId);
    if (group) addSubDelegation(root, group);
  }

  // 부모가 계약을 낸 뒤에야 자식이 시작하므로 위에서 아래로 시각을 정한다.
  const walkStart = (draft: Draft) => {
    if (draft.children.length === 0) return;
    const decideAt = draft.startAt + DECIDE_MS;
    draft.children.forEach((child) => {
      child.startAt = decideAt;
      walkStart(child);
    });
  };
  walkStart(root);

  const timeline: PlanEvent[] = [];
  const nodes: PlannedExecution[] = [];
  let delegations = 0;
  let costUsd = 0;
  let durationMs = 0;

  const visit = (draft: Draft, parentKey: string | null, seed: number) => {
    nodes.push({
      key: draft.key,
      agentId: draft.agentId,
      parentKey,
      prompt: draft.prompt,
      sessionId: mockSessionId(seed),
    });
    timeline.push({ at: draft.startAt, key: draft.key, status: 'RUNNING' });

    // 자식 실행을 먼저 돌려야 부모의 소요 시간을 자식 위에 쌓을 수 있다(자식은 병렬이므로 합이 아니라 최대).
    draft.children.forEach((child, index) => visit(child, draft.key, seed * 10 + index + 1));

    const judge = draft.children.length > 0;
    const childSpan = judge ? Math.max(...draft.children.map((child) => child.metrics?.durationMs ?? 0)) : 0;
    const metrics = mockMetrics(seed, judge, childSpan);
    const done = finishAt(draft);
    draft.metrics = metrics;

    costUsd += metrics.costUsd;
    // 화면 위쪽 요약에 쓸 값은 루트 실행의 소요 시간이다.
    if (parentKey === null) durationMs = metrics.durationMs;

    if (judge) {
      const first = draft.children[0];
      const childrenDoneAt = Math.max(...draft.children.map(finishAt));
      delegations += 1;
      timeline.push({
        at: draft.startAt + DECIDE_MS,
        key: draft.key,
        status: 'WAITING_CHILD',
        decision: { action: 'delegate', targetAgentId: first.agentId, prompt: first.prompt },
      });
      timeline.push({ at: childrenDoneAt, key: draft.key, status: 'RUNNING' });
    }

    const summary = judge ? `${draft.children.length}개 실행의 결과를 모아 정리했습니다.` : summaryFor(draft);
    const event: PlanEvent = {
      at: done,
      key: draft.key,
      status: 'DONE',
      handoff: summaryForHandoff(draft, summary),
      metrics,
    };
    // 위임한 실행은 위임 대상이 화면에 남아야 하므로 마지막 계약으로 덮지 않는다.
    if (!judge) event.decision = { action: 'done', summary };
    timeline.push(event);
  };
  visit(root, null, 1);

  return {
    nodes,
    timeline,
    teamNames: teams.map((group) => group.name),
    executions: nodes.length,
    delegations,
    costUsd,
    durationMs,
  };
}

/** 트리 계산용 임시 노드. 시각은 두 번의 순회로 채운다. */
interface Draft {
  key: string;
  agentId: number;
  prompt: string;
  teamName: string | null;
  startAt: number;
  children: Draft[];
  /** 자식을 먼저 돌린 뒤 채워진다(부모 소요 시간을 자식 위에 쌓기 위해). */
  metrics: ExecutionMetrics | null;
}

/** 자식이 다 끝나야 부모가 마무리하므로 아래에서 위로 올라온다. */
function finishAt(draft: Draft): number {
  if (draft.children.length === 0) return draft.startAt + WORK_MS;
  return Math.max(...draft.children.map(finishAt)) + FINALIZE_MS;
}

/**
 * 리더의 판단: 팀이 작으면(리더 + 1명) 리더가 직접 처리하고, 멤버가 더 있으면 하위 한 명에게 넘긴다.
 * 실제로는 이 판단도 LLM 이 하지만, 모의에서는 이 규칙으로 고정해 두어야 화면을 예측할 수 있다.
 */
function addSubDelegation(leader: Draft, group: AgentGroup): void {
  const others = group.memberIds.filter((id) => id !== leader.agentId);
  if (others.length < 2) return;
  leader.children.push({
    key: `${leader.key}w`,
    agentId: others[0],
    prompt: leader.prompt,
    teamName: group.name,
    startAt: 0,
    children: [],
    metrics: null,
  });
}

function summaryFor(draft: Draft): string {
  if (draft.teamName === null) return '요청을 직접 확인하고 처리했습니다.';
  return TEAM_SUMMARY[draft.teamName] ?? DEFAULT_SUMMARY;
}

function summaryForHandoff(draft: Draft, summary: string): { summary: string; changedFiles: string[] } {
  // 판단만 한 실행(마스터·리더)은 자기 파일을 만들지 않는다. 결과는 자식들이 낸다.
  if (draft.children.length > 0 || draft.teamName === null) return { summary, changedFiles: [] };
  return { summary, changedFiles: TEAM_FILES[draft.teamName] ?? [] };
}

/** 0 이상 1 미만의 결정적 값. 새로고침해도 같은 실행은 같은 지표를 갖는다. */
function pseudo(seed: number): number {
  return ((seed * 9301 + 49297) % 233280) / 233280;
}

/**
 * 실행 하나의 모의 지표.
 * 판단 실행(마스터·리더)은 한 번의 실행 안에서 **판단과 취합 두 스텝**을 돌기 때문에 호출량이 두 배다.
 */
function mockMetrics(seed: number, judge: boolean, childSpanMs: number): ExecutionMetrics {
  const calls = judge ? 2 : 1;
  const inputTokens = calls * (40000 + Math.round(pseudo(seed) * 8000));
  const outputTokens = calls * (200 + Math.round(pseudo(seed + 11) * (judge ? 400 : 2600)));
  const costUsd = inputTokens * 6e-6 + outputTokens * 3e-5;
  const own = judge ? 2600 + Math.round(pseudo(seed + 23) * 2400) : 18000 + Math.round(pseudo(seed + 29) * 70000);
  return { inputTokens, outputTokens, costUsd, durationMs: own + childSpanMs };
}

/** 세션 재개(`--resume`)에 쓰는 실행 세션 id(모의). */
function mockSessionId(seed: number): string {
  const head = ((seed * 2654435761) % 4294967296).toString(16).padStart(8, '0');
  const tail = ((seed * 40503) % 65536).toString(16).padStart(4, '0');
  return `${head}-${tail}`;
}

function short(text: string, max = 28): string {
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

/** 경로에서 파일 이름만 뽑는다(화면에 짧게 보여 주려고). */
export function fileName(path: string): string {
  return path.split('/').pop() ?? path;
}

/** 첨부한 파일을 지시 한 줄에 붙인다. 마스터가 하위로 넘길 때도 같은 문장이 따라간다. */
function withAttachments(prompt: string, attachments: AttachedFile[]): string {
  if (attachments.length === 0) return prompt;
  return `${prompt} · 첨부: ${attachments.map((file) => fileName(file.path)).join(', ')}`;
}
