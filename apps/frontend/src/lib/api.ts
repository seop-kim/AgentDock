const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';

/** 서버가 2xx 가 아닌 응답을 주면 던진다. 404 같은 상태를 호출부가 구분할 수 있게 status 를 담는다. */
class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    cache: 'no-store',
  });
  if (!res.ok) {
    const body = await res.text();
    throw new ApiError(res.status, `${res.status} ${res.statusText}: ${body}`);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

export interface Capabilities {
  models?: string[] | null;
  modes?: string[] | null;
  notes?: string | null;
  install?: string[] | null;
  installRequire?: string | null;
  installPrerequisite?: string[] | null;
}

export interface Provider {
  id: number;
  key: string;
  name: string;
  enabled: boolean;
  capabilities?: Capabilities | null;
}

export interface CliStatus {
  resolvedPath: string | null;
  version: string | null;
  runnable: boolean;
  detail: string | null;
}

export interface Workspace {
  id: number;
  name: string;
  path: string;
}

export interface WorkspaceRuntime {
  providerId: number;
  providerKey: string;
  name: string;
  enabled: boolean;
  status: string;
  cliMissing: boolean;
  lastCheckedAt: string | null;
  lastError: string | null;
}

export interface ProjectWorkspaceInfo {
  workspaceId: number;
  name: string;
  path: string;
  isDefault: boolean;
}

export interface AgentRole {
  id: number;
  name: string;
}

export interface PermissionProfile {
  id: number;
  name: string;
}

export interface AgentSummary {
  id: number;
  name: string;
}

export interface Project {
  id: number;
  name: string;
  description?: string | null;
  workspaces: ProjectWorkspaceInfo[];
  masterAgent?: AgentSummary | null;
  masterPrompt?: string;
}

export interface Agent {
  id: number;
  name: string;
  projectId: number;
  role: AgentRole;
  permissionProfile: PermissionProfile;
  provider: Provider;
  providerId: number;
  persona: string | null;
  model: string | null;
  mode: string | null;
  placed: boolean;
  nodeX: number | null;
  nodeY: number | null;
  available: boolean;
  unavailableReason: 'PROVIDER_DELETED' | 'RUNTIME_DISABLED' | 'CONNECTION_NOT_CONNECTED' | null;
}

export interface AgentGroup {
  id: number;
  projectId: number;
  name: string;
  /** 그룹 프롬프트와 별개인 공유 노트(그룹 안 실행들이 함께 보는 맥락). */
  sharedNote: string;
  description?: string | null;
  prompt: string | null;
  leader: AgentSummary | null;
  members: AgentSummary[];
  nodeX: number | null;
  nodeY: number | null;
}

export interface Task {
  id: number;
  title: string;
  prompt: string;
  status: string;
  group: AgentSummary | null;
  agent: AgentSummary | null;
  latestExecutionId: number | null;
}

export interface Execution {
  id: number;
  agentId: number;
  taskId: number | null;
  prompt: string;
  status: string;
  exitCode: number | null;
  errorMessage: string | null;
  resultText: string | null;
  decision: 'DELEGATE' | 'ASK' | 'DONE' | null;
  delegatedTargetAgentId: number | null;
  parentExecutionId: number | null;
  rootExecutionId: number | null;
  inputTokens: number | null;
  outputTokens: number | null;
  costUsd: number | null;
  durationMs: number | null;
  numTurns: number | null;
  sessionId: string | null;
  /** 이 실행 트리가 도는 git worktree 경로. 격리하지 못하면 null(워크스페이스에서 실행). */
  worktreePath: string | null;
  /** worktree 의 브랜치 이름(`agentdock/exec-<루트실행id>`). */
  worktreeBranch: string | null;
  /** 트리 브랜치에 남긴 커밋의 짧은 sha(루트에만). 커밋할 것이 없으면 null. */
  resultCommit: string | null;
  /** 트리 결과를 메인 저장소로 되돌린 결과(루트에만): MERGED / MANUAL / NONE. */
  mergeStatus: 'MERGED' | 'MANUAL' | 'NONE' | null;
  /** 자동 병합하지 못한 사유(MANUAL 일 때). */
  mergeDetail: string | null;
  /** 트리가 바꾼 파일 목록(`{status, path}`). 커밋·병합하지 못했으면 빈 배열. */
  changedFiles: { status: string; path: string }[] | null;
  /** 판단 실행이 사람에게 물은 질문(계약 `ask`). 답을 기다리는 동안 채워진다. */
  question: string | null;
  /** 그 질문에 함께 온 보기(`ask` 의 options). 화면이 답을 버튼으로 그린다. 없으면 빈 배열. */
  questionOptions: string[] | null;
  /** 그 질문에 대한 사람의 답. */
  answer: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface ExecutionTreeNode {
  execution: Execution;
  depth: number;
  agentName: string | null;
  targetAgentName: string | null;
  logCount: number;
}

export interface ExecutionTree {
  rootExecutionId: number;
  nodes: ExecutionTreeNode[];
}

export interface ExecutionLogEntry {
  id: number;
  stream: string;
  content: string;
}

export interface Attachment {
  id: number;
  workspaceId: number;
  taskId: number | null;
  originalName: string;
  storedPath: string;
  sizeBytes: number | null;
}

export interface WorkspaceBrowseEntry {
  name: string;
  path: string;
}

export interface WorkspaceBrowseResult {
  path: string | null;
  parentPath: string | null;
  entries: WorkspaceBrowseEntry[];
}

export const api = {
  base: API_BASE,

  listProjects: () => request<Project[]>('/projects'),
  getProject: (id: number) => request<Project>(`/projects/${id}`),
  createProject: (data: { name: string; description?: string }) =>
    request<Project>('/projects', { method: 'POST', body: JSON.stringify(data) }),
  updateProject: (id: number, data: { name: string; description?: string }) =>
    request<Project>(`/projects/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteProject: (id: number) => request<void>(`/projects/${id}`, { method: 'DELETE' }),
  setProjectMaster: (id: number, data: { agentId: number | null; masterPrompt?: string }) =>
    request<Project>(`/projects/${id}/master`, { method: 'PUT', body: JSON.stringify(data) }),
  assignProjectWorkspace: (id: number, workspaceId: number, isDefault = false) =>
    request<Project>(`/projects/${id}/workspaces`, {
      method: 'POST',
      body: JSON.stringify({ workspaceId, isDefault }),
    }),
  removeProjectWorkspace: (id: number, workspaceId: number) =>
    request<void>(`/projects/${id}/workspaces/${workspaceId}`, { method: 'DELETE' }),
  saveLayout: (
    id: number,
    data: {
      agents: { agentId: number; x: number | null; y: number | null; placed: boolean }[];
      groups: { groupId: number; x: number | null; y: number | null }[];
    },
  ) => request<void>(`/projects/${id}/layout`, { method: 'PUT', body: JSON.stringify(data) }),
  clearLayout: (id: number) => request<void>(`/projects/${id}/layout`, { method: 'DELETE' }),

  listAgents: () => request<Agent[]>('/agents'),
  createAgent: (data: Record<string, unknown>) =>
    request<Agent>('/agents', { method: 'POST', body: JSON.stringify(data) }),
  updateAgent: (id: number, data: Record<string, unknown>) =>
    request<Agent>(`/agents/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteAgent: (id: number) => request<void>(`/agents/${id}`, { method: 'DELETE' }),

  listGroups: (projectId: number) => request<AgentGroup[]>(`/groups?projectId=${projectId}`),
  createGroup: (data: { projectId: number; name: string; description?: string; leaderAgentId?: number }) =>
    request<AgentGroup>('/groups', { method: 'POST', body: JSON.stringify(data) }),
  updateGroup: (
    id: number,
    data: { name: string; description?: string; prompt?: string; sharedNote?: string; leaderAgentId?: number | null },
  ) => request<AgentGroup>(`/groups/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteGroup: (id: number) => request<void>(`/groups/${id}`, { method: 'DELETE' }),
  addGroupMember: (groupId: number, agentId: number) =>
    request<AgentGroup>(`/groups/${groupId}/members`, { method: 'POST', body: JSON.stringify({ agentId }) }),
  removeGroupMember: (groupId: number, agentId: number) =>
    request<AgentGroup>(`/groups/${groupId}/members/${agentId}`, { method: 'DELETE' }),

  listTasks: (projectId: number) => request<Task[]>(`/tasks?projectId=${projectId}`),
  sendCommand: (
    projectId: number,
    data: { text: string; targetAgentId?: number | null; groupId?: number | null; attachmentIds?: number[] },
  ) => request<{ taskId: number; rootExecutionId: number }>(`/projects/${projectId}/commands`, {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  getExecution: (id: number) => request<Execution>(`/executions/${id}`),
  getExecutionTree: (id: number) => request<ExecutionTree>(`/executions/${id}/tree`),
  getExecutionLogs: (id: number) => request<ExecutionLogEntry[]>(`/executions/${id}/logs`),
  /**
   * 실행 하나를 취소한다(프로세스 종료 + 상태 전이는 서버가 한다).
   * 이미 없어진 실행(404)은 오류로 보지 않고 `{cancelled:false}` 로 돌려준다 —
   * 화면은 실제 상태를 다시 읽어 보여 준다.
   */
  cancelExecution: async (id: number) => {
    try {
      return await request<{ cancelled: boolean }>(`/executions/${id}/cancel`, { method: 'POST' });
    } catch (ex) {
      if (ex instanceof ApiError && ex.status === 404) return { cancelled: false };
      throw ex;
    }
  },
  /** 워크트리 정리(사람이 판단해 부른다). deleteBranch 면 전용 브랜치도 함께 지운다. 도는 실행이면 409. */
  removeExecutionWorktree: (id: number, deleteBranch = false) =>
    request<void>(`/executions/${id}/worktree${deleteBranch ? '?branch=true' : ''}`, { method: 'DELETE' }),
  /** 자동 병합이 MANUAL 로 끝난 트리를 다시 병합한다(사람이 변경을 정리한 뒤). 도는 트리면 409. */
  retryExecutionMerge: (id: number) => request<Execution>(`/executions/${id}/merge`, { method: 'POST' }),
  /** `WAITING_INPUT` 인 실행에 사람의 답을 넣고 그 실행을 이어서 돌린다. 입력 대기가 아니면 409. */
  answerExecution: (id: number, text: string) =>
    request<Execution>(`/executions/${id}/answer`, { method: 'POST', body: JSON.stringify({ text }) }),

  listProviders: () => request<Provider[]>('/ai-providers'),
  createProvider: (data: { key: string; name: string }) =>
    request<Provider>('/ai-providers', { method: 'POST', body: JSON.stringify(data) }),
  deleteProvider: (id: number) => request<void>(`/ai-providers/${id}`, { method: 'DELETE' }),
  setProviderEnabled: (id: number, enabled: boolean) =>
    request<Provider>(`/ai-providers/${id}/enabled`, { method: 'PUT', body: JSON.stringify({ enabled }) }),
  updateProviderCapabilities: (id: number, data: Capabilities) =>
    request<Provider>(`/ai-providers/${id}/capabilities`, { method: 'PUT', body: JSON.stringify(data) }),
  getProviderCli: (id: number) => request<CliStatus>(`/ai-providers/${id}/cli`),
  startLogin: (providerId: number) => request<{ sessionId: string }>(`/ai-providers/${providerId}/login`, { method: 'POST' }),
  startInstall: (providerId: number) => request<{ sessionId: string }>(`/ai-providers/${providerId}/install`, { method: 'POST' }),
  sendSessionInput: (sessionId: string, text: string) =>
    request<void>(`/ai-providers/command-sessions/${sessionId}/input`, {
      method: 'POST',
      body: JSON.stringify({ text }),
    }),
  stopSession: (sessionId: string) => request<void>(`/ai-providers/command-sessions/${sessionId}`, { method: 'DELETE' }),

  listWorkspaces: () => request<Workspace[]>('/workspaces'),
  createWorkspace: (data: { name: string; path: string; description?: string }) =>
    request<Workspace>('/workspaces', { method: 'POST', body: JSON.stringify(data) }),
  browseWorkspace: (path?: string) =>
    request<WorkspaceBrowseResult>(`/workspaces/browse${path ? `?path=${encodeURIComponent(path)}` : ''}`),
  listWorkspaceFiles: (workspaceId: number) => request<string[]>(`/workspaces/${workspaceId}/files`),
  uploadAttachments: async (workspaceId: number, files: File[]): Promise<Attachment[]> => {
    const form = new FormData();
    files.forEach((file) => form.append('files', file, file.name));
    const res = await fetch(`${API_BASE}/workspaces/${workspaceId}/attachments`, { method: 'POST', body: form });
    if (!res.ok) {
      const body = await res.text();
      throw new Error(`${res.status} ${res.statusText}: ${body}`);
    }
    return (await res.json()) as Attachment[];
  },
  listWorkspaceRuntimes: (workspaceId: number) => request<WorkspaceRuntime[]>(`/workspaces/${workspaceId}/runtimes`),
  checkWorkspaceRuntime: (workspaceId: number, providerId: number) =>
    request<WorkspaceRuntime>(`/workspaces/${workspaceId}/runtimes/${providerId}/check`, { method: 'POST' }),

  listRoles: () => request<AgentRole[]>('/roles'),
  listPermissionProfiles: () => request<PermissionProfile[]>('/permission-profiles'),
};
