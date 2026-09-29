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

export interface AiProvider {
  id: number;
  key: string;
  name: string;
}

export interface Workspace {
  id: number;
  name: string;
  path: string;
}

export interface Project {
  id: number;
  name: string;
  description?: string | null;
  workspaceId: number;
  workspace: Workspace | null;
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
  project: Project | null;
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
  role: AgentRole;
  permissionProfile: PermissionProfile;
  provider: AiProvider;
  model: string | null;
  mode: string | null;
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

  listWorkspaces: () => request<Workspace[]>('/workspaces'),
  createWorkspace: (data: { name: string; path: string; description?: string }) =>
    request<Workspace>('/workspaces', { method: 'POST', body: JSON.stringify(data) }),
  browseWorkspace: (path?: string) =>
    request<WorkspaceBrowseResult>(`/workspaces/browse${path ? `?path=${encodeURIComponent(path)}` : ''}`),

  listProjects: () => request<Project[]>('/projects'),
  createProject: (data: { name: string; workspaceId: number; description?: string }) =>
    request<Project>('/projects', { method: 'POST', body: JSON.stringify(data) }),

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
  createAgent: (data: Record<string, unknown>) =>
    request<Agent>('/agents', { method: 'POST', body: JSON.stringify(data) }),

  createExecution: (data: { agentId: number; projectId: number; prompt: string }) =>
    request<Execution>('/executions', { method: 'POST', body: JSON.stringify(data) }),
  getExecution: (id: number | string) => request<Execution>(`/executions/${id}`),
};
