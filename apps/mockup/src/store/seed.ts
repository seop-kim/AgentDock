import type {
  Agent,
  AgentGroup,
  AgentRole,
  AiProvider,
  Capabilities,
  CliStatus,
  PermissionProfile,
  Project,
  Task,
  TaskStatus,
  Workspace,
} from '../types';

/** 등록 가능한 런타임 종류와 기본 표시 이름. 삭제한 런타임은 다시 등록할 수 있다. */
export const DEFAULT_PROVIDER_NAMES: Record<string, string> = {
  CLAUDE_CODE: 'Claude Code',
  CODEX: 'Codex',
  COMMAND_CODE: 'Command Code',
  GEMINI: 'Gemini',
};

const NPM_PREREQUISITE = ['nvm install lts', 'nvm use lts'];

/** 런타임을 새로 등록할 때 들어가는 초기 capabilities(V5/V9 시드와 같은 값). */
export const DEFAULT_CAPABILITIES: Record<string, Capabilities> = {
  CLAUDE_CODE: {
    models: ['opus', 'sonnet', 'fable'],
    modes: ['acceptEdits', 'auto', 'bypassPermissions', 'manual', 'dontAsk', 'plan'],
    notes: 'models 는 `claude --help` 에 나온 alias 예시이며 CLI 버전에 따라 바뀐다. 화면에서 편집한다.',
    install: ['npm install -g @anthropic-ai/claude-code'],
    installRequire: 'npm',
    installPrerequisite: NPM_PREREQUISITE,
  },
  CODEX: {
    models: [],
    modes: [],
    notes: 'CLI 가 이 머신에 없어 미확인. 화면에서 입력한다.',
    install: ['npm install -g @openai/codex'],
    installRequire: 'npm',
    installPrerequisite: NPM_PREREQUISITE,
  },
  COMMAND_CODE: {
    models: [],
    modes: [],
    notes: 'CLI 가 이 머신에 없어 미확인. 화면에서 입력한다.',
    install: ['npm install -g command-code'],
    installRequire: 'npm',
    installPrerequisite: NPM_PREREQUISITE,
  },
  GEMINI: {
    models: [],
    modes: [],
    notes: 'CLI 가 이 머신에 없어 미확인. 화면에서 입력한다.',
    install: ['npm install -g @google/gemini-cli'],
    installRequire: 'npm',
    installPrerequisite: NPM_PREREQUISITE,
  },
};

/** 현재 개발 DB 와 같은 초기 상태: Claude / Command Code 켜짐, Codex / Gemini 는 삭제된 상태. */
export const SEED_PROVIDERS: AiProvider[] = [
  { id: 12, key: 'CLAUDE_CODE', name: 'Claude Code', enabled: true, capabilities: DEFAULT_CAPABILITIES.CLAUDE_CODE },
  { id: 13, key: 'COMMAND_CODE', name: 'Command Code', enabled: true, capabilities: DEFAULT_CAPABILITIES.COMMAND_CODE },
];

/** CLI 확인 창에 보이는 가짜 결과. Claude 만 설치된 것으로 둔다. */
export const CLI_STATUS_BY_KEY: Record<string, CliStatus> = {
  CLAUDE_CODE: {
    runnable: true,
    resolvedPath: 'C:\\Users\\mock\\.local\\bin\\claude.exe',
    version: '2.1.227 (Claude Code)',
    detail: null,
  },
};

export const CLI_STATUS_UNKNOWN: CliStatus = {
  runnable: false,
  resolvedPath: null,
  version: null,
  detail: 'CLI 정보를 확인할 수 없습니다',
};

export const SEED_WORKSPACES: Workspace[] = [
  { id: 1, name: 'agentDock', path: 'C:\\Users\\mock\\Documents\\GitHub\\AgentDock' },
  { id: 2, name: 'shop-web', path: 'C:\\Users\\mock\\Documents\\GitHub\\shop-web' },
  { id: 3, name: 'shop-api', path: 'C:\\Users\\mock\\Documents\\GitHub\\shop-api' },
];

/** 프로젝트↔워크스페이스는 N:N 이고 프로젝트당 기본(★) 1개다. */
export const SEED_PROJECTS: Project[] = [
  { id: 1, name: 'test', workspaces: [{ workspaceId: 1, isDefault: true }] },
  {
    id: 2,
    name: 'shop',
    workspaces: [
      { workspaceId: 2, isDefault: true },
      { workspaceId: 3, isDefault: false },
    ],
  },
  { id: 3, name: 'blog', workspaces: [] },
];

export const SEED_ROLES: AgentRole[] = [
  { id: 1, name: 'Backend Developer' },
  { id: 2, name: 'Frontend Developer' },
  { id: 3, name: 'Reviewer' },
  { id: 4, name: 'Planner' },
];

export const SEED_PERMISSION_PROFILES: PermissionProfile[] = [
  { id: 1, name: 'Developer Default (읽기·쓰기·터미널)' },
  { id: 2, name: 'Read Only (읽기만)' },
];

/* ---------------------------------------------------------------------------
 * 테스트 데이터 — shop 프로젝트: 그룹 5개, 에이전트 30개.
 *   그룹 소속 20 / 그룹 없이 구성도에만 배치 6 / 구성도에 놓지 않음(미배치) 4.
 *   에이전트마다 맡은 Task 상태를 달리해 카드에 상태가 골고루 보이게 한다.
 * ------------------------------------------------------------------------- */

const SHOP_PROJECT_ID = 2;
/** project 1 의 test 에이전트가 id 1 이므로 shop 에이전트는 2 부터 이어 붙인다. */
const SHOP_FIRST_AGENT_ID = 2;

/** 그룹 5개. 이름 순서가 그룹 id(1~5)이고, 첫 멤버가 리더가 된다. */
const SHOP_GROUP_NAMES = ['Backend Team', 'Frontend Team', 'QA Team', 'DevOps Team', 'Docs Team'];

/** 모델/모드를 조금씩 다르게 넣어 구성도에 다양하게 보이게 한다(빈 값은 CLI 기본값). */
const MODEL_CYCLE = ['opus', 'sonnet', ''];
const MODE_CYCLE = ['acceptEdits', 'plan', ''];

/** shop 에이전트 한 개의 구성. group 이 없으면 그룹 없이 구성도에만 놓인다. */
interface ShopAgentSpec {
  name: string;
  roleId: number;
  group?: string;
  /** 구성도에 놓지 않는다(에이전트 목록에만) */
  unplaced?: boolean;
  /** 맡은 Task 상태. 없으면 작업 없음 */
  task?: TaskStatus;
}

const SHOP_AGENTS: ShopAgentSpec[] = [
  // 그룹 소속 (20)
  { name: 'Backend Dev A', roleId: 1, group: 'Backend Team', task: 'RUNNING' },
  { name: 'Backend Dev B', roleId: 1, group: 'Backend Team' },
  { name: 'Backend Dev C', roleId: 1, group: 'Backend Team', task: 'PENDING' },
  { name: 'Backend Dev D', roleId: 1, group: 'Backend Team' },
  { name: 'Backend Dev E', roleId: 1, group: 'Backend Team', task: 'RUNNING' },
  { name: 'Backend Dev F', roleId: 1, group: 'Backend Team' },
  { name: 'Frontend Dev A', roleId: 2, group: 'Frontend Team', task: 'PENDING' },
  { name: 'Frontend Dev B', roleId: 2, group: 'Frontend Team' },
  { name: 'Frontend Dev C', roleId: 2, group: 'Frontend Team', task: 'RUNNING' },
  { name: 'Frontend Dev D', roleId: 2, group: 'Frontend Team' },
  { name: 'Frontend Dev E', roleId: 2, group: 'Frontend Team' },
  { name: 'QA A', roleId: 3, group: 'QA Team', task: 'RUNNING' },
  { name: 'QA B', roleId: 3, group: 'QA Team' },
  { name: 'QA C', roleId: 3, group: 'QA Team', task: 'PENDING' },
  { name: 'QA D', roleId: 3, group: 'QA Team' },
  { name: 'DevOps A', roleId: 1, group: 'DevOps Team' },
  { name: 'DevOps B', roleId: 1, group: 'DevOps Team', task: 'RUNNING' },
  { name: 'DevOps C', roleId: 1, group: 'DevOps Team' },
  { name: 'Docs A', roleId: 4, group: 'Docs Team' },
  { name: 'Docs B', roleId: 4, group: 'Docs Team', task: 'PENDING' },
  // 그룹 없이 구성도에만 배치 (6)
  { name: 'Reviewer A', roleId: 3, task: 'RUNNING' },
  { name: 'Reviewer B', roleId: 3 },
  { name: 'Reviewer C', roleId: 3 },
  { name: 'Planner A', roleId: 4 },
  { name: 'Planner B', roleId: 4 },
  { name: 'Planner C', roleId: 4 },
  // 구성도에 놓지 않음(미배치) (4)
  { name: 'Reviewer D', roleId: 3, unplaced: true },
  { name: 'Reviewer E', roleId: 3, unplaced: true },
  { name: 'Planner D', roleId: 4, unplaced: true },
  { name: 'Planner E', roleId: 4, unplaced: true },
];

const shopAgents: Agent[] = SHOP_AGENTS.map((spec, index) => ({
  id: SHOP_FIRST_AGENT_ID + index,
  projectId: SHOP_PROJECT_ID,
  name: spec.name,
  roleId: spec.roleId,
  // Reviewer 는 읽기 전용 프로필을 쓴다.
  permissionProfileId: spec.roleId === 3 ? 2 : 1,
  providerId: index % 3 === 0 ? 13 : 12,
  persona: '',
  model: MODEL_CYCLE[index % MODEL_CYCLE.length],
  mode: MODE_CYCLE[index % MODE_CYCLE.length],
  placed: !spec.unplaced,
}));

const shopGroups: AgentGroup[] = SHOP_GROUP_NAMES.map((name, index) => {
  const memberIds = SHOP_AGENTS.flatMap((spec, agentIndex) =>
    spec.group === name ? [SHOP_FIRST_AGENT_ID + agentIndex] : [],
  );
  return {
    id: index + 1,
    projectId: SHOP_PROJECT_ID,
    name,
    leaderAgentId: memberIds[0] ?? null,
    memberIds,
  };
});

let nextTaskId = 3;

export const SEED_AGENTS: Agent[] = [
  { id: 1, projectId: 1, name: 'test', roleId: 1, permissionProfileId: 1, providerId: 12, persona: '', model: '', mode: '', placed: true },
  ...shopAgents,
];

export const SEED_GROUPS: AgentGroup[] = shopGroups;

/** 상태(작업 중/대기중/없음)가 골고루 보이도록 맡은 Task 가 있는 에이전트만 Task 를 하나씩 갖는다. */
export const SEED_TASKS: Task[] = [
  { id: 1, projectId: 1, agentId: 1, title: '연결 확인', status: 'DONE' },
  { id: 2, projectId: 1, agentId: 1, title: 'README 정리', status: 'PENDING' },
  ...SHOP_AGENTS.flatMap((spec, index): Task[] => {
    if (!spec.task) return [];
    return [
      {
        id: nextTaskId++,
        projectId: SHOP_PROJECT_ID,
        agentId: SHOP_FIRST_AGENT_ID + index,
        title: `${spec.name} 작업`,
        status: spec.task,
      },
    ];
  }),
];
