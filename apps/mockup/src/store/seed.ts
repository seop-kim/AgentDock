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

/** Planner(5)는 구성도에 놓지 않은 상태(미배치)로 둔다. 에이전트 목록에만 보인다. */
export const SEED_AGENTS: Agent[] = [
  { id: 1, projectId: 1, name: 'test', roleId: 1, permissionProfileId: 1, providerId: 12, persona: '', model: '', mode: '', placed: true },
  {
    id: 2,
    projectId: 2,
    name: 'Backend Dev A',
    roleId: 1,
    permissionProfileId: 1,
    providerId: 12,
    persona: '테스트를 먼저 작성하고 작은 단위로 커밋한다.',
    model: 'opus',
    mode: 'acceptEdits',
    placed: true,
  },
  {
    id: 3,
    projectId: 2,
    name: 'Frontend Dev A',
    roleId: 2,
    permissionProfileId: 1,
    providerId: 12,
    persona: '',
    model: 'sonnet',
    mode: 'plan',
    placed: true,
  },
  {
    id: 4,
    projectId: 2,
    name: 'Reviewer',
    roleId: 3,
    permissionProfileId: 2,
    providerId: 13,
    persona: '변경 범위와 회귀 위험을 먼저 본다.',
    model: '',
    mode: '',
    placed: true,
  },
  {
    id: 5,
    projectId: 2,
    name: 'Planner',
    roleId: 4,
    permissionProfileId: 2,
    providerId: 12,
    persona: '',
    model: 'sonnet',
    mode: 'plan',
    placed: false,
  },
];

export const SEED_GROUPS: AgentGroup[] = [
  { id: 1, projectId: 2, name: 'Backend Team', leaderAgentId: 2, memberIds: [2, 4] },
  { id: 2, projectId: 2, name: 'Frontend Team', leaderAgentId: 3, memberIds: [3] },
];

/** 에이전트 상태(작업 중/대기중/없음)가 모두 보이도록 담당(agentId)과 상태를 나눠 둔다. */
export const SEED_TASKS: Task[] = [
  { id: 1, projectId: 1, agentId: 1, title: '연결 확인', status: 'DONE' },
  { id: 2, projectId: 1, agentId: 1, title: 'README 정리', status: 'PENDING' },
  { id: 3, projectId: 2, agentId: 2, title: '주문 API 추가', status: 'RUNNING' },
  { id: 4, projectId: 2, agentId: 3, title: '장바구니 화면', status: 'PENDING' },
  { id: 5, projectId: 2, agentId: 2, title: '결제 오류 수정', status: 'FAILED' },
  { id: 6, projectId: 2, agentId: 3, title: '상품 목록 페이징', status: 'DONE' },
  { id: 7, projectId: 2, agentId: 4, title: '코드 리뷰', status: 'DONE' },
];
