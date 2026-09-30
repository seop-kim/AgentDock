const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    cache: 'no-store',
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`${res.status} ${res.statusText}: ${body}`);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

export interface AgentRole {
  id: number;
  name: string;
  description?: string | null;
}

export interface PermissionProfile {
  id: number;
  name: string;
}

export interface ProviderCapabilities {
  models?: string[] | null;
  modes?: string[] | null;
  notes?: string | null;
  /** CLI 설치 단계(순서대로 실행). 셸을 통해 실행된다. */
  install?: string[] | null;
  /** 먼저 있어야 하는 실행 파일(예: npm). 없으면 installPrerequisite 를 먼저 실행한다. */
  installRequire?: string | null;
  /** 필수 실행 파일이 없을 때 실행할 단계들(예: nvm install lts). */
  installPrerequisite?: string[] | null;
}

export interface AiProvider {
  id: number;
  key: string;
  name: string;
  capabilities?: ProviderCapabilities | null;
  enabled: boolean;
}

/** 런타임 CLI 실행 파일 상태(폴더와 무관한 런타임 전역). */
export interface CliStatus {
  resolvedPath: string | null;
  version: string | null;
  runnable: boolean;
  detail: string | null;
}

/** 워크스페이스(폴더)에서의 런타임 상태. */
export interface WorkspaceRuntime {
  providerId: number;
  providerKey: string;
  name: string;
  enabled: boolean;
  status: string;
  cliMissing: boolean;
  install: string[];
  installRequire: string | null;
  lastCheckedAt: string | null;
  lastError: string | null;
}

export interface Workspace {
  id: number;
  name: string;
  path: string;
}

export interface ProjectWorkspaceInfo {
  workspaceId: number;
  name: string;
  path: string;
  isDefault: boolean;
}

export interface Project {
  id: number;
  name: string;
  description?: string | null;
  workspaces: ProjectWorkspaceInfo[];
}

export interface ProjectSummary {
  id: number;
  name: string;
}

export interface AgentSummary {
  id: number;
  name: string;
}

export interface AgentGroup {
  id: number;
  projectId: number;
  name: string;
  description?: string | null;
  leader: AgentSummary | null;
  members: AgentSummary[];
}

export interface Task {
  id: number;
  project: ProjectSummary | null;
  group: AgentSummary | null;
  agent: AgentSummary | null;
  title: string;
  prompt: string;
  status: string;
  latestExecutionId: number | null;
}

export interface Agent {
  id: number;
  name: string;
  projectId: number;
  project: ProjectSummary | null;
  role: AgentRole;
  permissionProfile: PermissionProfile;
  provider: AiProvider;
  providerId: number;
  persona: string | null;
  model: string | null;
  mode: string | null;
  available: boolean;
  unavailableReason: 'PROVIDER_DELETED' | 'RUNTIME_DISABLED' | 'CONNECTION_NOT_CONNECTED' | null;
}

export interface Execution {
  id: number;
  status: string;
  prompt: string;
  exitCode: number | null;
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
  listRoles: () => request<AgentRole[]>('/roles'),
  createRole: (data: { name: string; description?: string }) =>
    request<AgentRole>('/roles', { method: 'POST', body: JSON.stringify(data) }),

  listPermissionProfiles: () => request<PermissionProfile[]>('/permission-profiles'),
  createPermissionProfile: (data: Record<string, unknown>) =>
    request<PermissionProfile>('/permission-profiles', { method: 'POST', body: JSON.stringify(data) }),

  listProviders: () => request<AiProvider[]>('/ai-providers'),
  createProvider: (data: { key: string; name: string }) =>
    request<AiProvider>('/ai-providers', { method: 'POST', body: JSON.stringify(data) }),
  deleteProvider: (id: number) => request<void>(`/ai-providers/${id}`, { method: 'DELETE' }),
  setProviderEnabled: (id: number, enabled: boolean) =>
    request<AiProvider>(`/ai-providers/${id}/enabled`, { method: 'PUT', body: JSON.stringify({ enabled }) }),
  updateProviderCapabilities: (id: number, data: ProviderCapabilities) =>
    request<AiProvider>(`/ai-providers/${id}/capabilities`, { method: 'PUT', body: JSON.stringify(data) }),
  getProviderCli: (providerId: number) => request<CliStatus>(`/ai-providers/${providerId}/cli`),
  startLogin: (providerId: number) =>
    request<{ sessionId: string }>(`/ai-providers/${providerId}/login`, { method: 'POST' }),
  startInstall: (providerId: number) =>
    request<{ sessionId: string }>(`/ai-providers/${providerId}/install`, { method: 'POST' }),
  sendSessionInput: (sessionId: string, text: string) =>
    request<void>(`/ai-providers/command-sessions/${sessionId}/input`, {
      method: 'POST',
      body: JSON.stringify({ text }),
    }),
  stopSession: (sessionId: string) =>
    request<void>(`/ai-providers/command-sessions/${sessionId}`, { method: 'DELETE' }),

  listWorkspaces: () => request<Workspace[]>('/workspaces'),
  createWorkspace: (data: { name: string; path: string; description?: string }) =>
    request<Workspace>('/workspaces', { method: 'POST', body: JSON.stringify(data) }),
  browseWorkspace: (path?: string) =>
    request<WorkspaceBrowseResult>(`/workspaces/browse${path ? `?path=${encodeURIComponent(path)}` : ''}`),
  listWorkspaceRuntimes: (workspaceId: number) => request<WorkspaceRuntime[]>(`/workspaces/${workspaceId}/runtimes`),
  checkWorkspaceRuntime: (workspaceId: number, providerId: number) =>
    request<WorkspaceRuntime>(`/workspaces/${workspaceId}/runtimes/${providerId}/check`, { method: 'POST' }),

  listProjects: () => request<Project[]>('/projects'),
  createProject: (data: { name: string; workspaceId?: number; description?: string }) =>
    request<Project>('/projects', { method: 'POST', body: JSON.stringify(data) }),
  assignProjectWorkspace: (projectId: number, workspaceId: number, isDefault = false) =>
    request<Project>(`/projects/${projectId}/workspaces`, {
      method: 'POST',
      body: JSON.stringify({ workspaceId, isDefault }),
    }),
  removeProjectWorkspace: (projectId: number, workspaceId: number) =>
    request<void>(`/projects/${projectId}/workspaces/${workspaceId}`, { method: 'DELETE' }),

  listGroups: (projectId?: number) =>
    request<AgentGroup[]>(`/groups${projectId ? `?projectId=${projectId}` : ''}`),
  createGroup: (data: { projectId: number; name: string; description?: string; leaderAgentId?: number }) =>
    request<AgentGroup>('/groups', { method: 'POST', body: JSON.stringify(data) }),
  updateGroup: (id: number, data: { name: string; description?: string; leaderAgentId?: number | null }) =>
    request<AgentGroup>(`/groups/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  addGroupMember: (groupId: number, agentId: number) =>
    request<AgentGroup>(`/groups/${groupId}/members`, { method: 'POST', body: JSON.stringify({ agentId }) }),
  removeGroupMember: (groupId: number, agentId: number) =>
    request<AgentGroup>(`/groups/${groupId}/members/${agentId}`, { method: 'DELETE' }),

  listTasks: (projectId?: number, groupId?: number) => {
    const params = new URLSearchParams();
    if (projectId) params.set('projectId', String(projectId));
    if (groupId) params.set('groupId', String(groupId));
    const query = params.toString();
    return request<Task[]>(`/tasks${query ? `?${query}` : ''}`);
  },
  createTask: (data: { projectId: number; title: string; prompt: string; groupId?: number; agentId?: number }) =>
    request<Task>('/tasks', { method: 'POST', body: JSON.stringify(data) }),
  runTask: (id: number) => request<Execution>(`/tasks/${id}/run`, { method: 'POST' }),

  listAgents: () => request<Agent[]>('/agents'),
  assignAgentProvider: (agentId: number, providerId: number) =>
    request<Agent>(`/agents/${agentId}/provider`, { method: 'PUT', body: JSON.stringify({ providerId }) }),
  createAgent: (data: Record<string, unknown>) =>
    request<Agent>('/agents', { method: 'POST', body: JSON.stringify(data) }),

  createExecution: (data: { agentId: number; projectId: number; prompt: string }) =>
    request<Execution>('/executions', { method: 'POST', body: JSON.stringify(data) }),
  getExecution: (id: number | string) => request<Execution>(`/executions/${id}`),
};
