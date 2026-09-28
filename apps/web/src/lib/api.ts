const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? 'http://localhost:3001';

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
  id: string;
  name: string;
  description?: string | null;
}

export interface PermissionProfile {
  id: string;
  name: string;
}

export interface AiProvider {
  id: string;
  key: string;
  name: string;
}

export interface Workspace {
  id: string;
  name: string;
  path: string;
}

export interface Agent {
  id: string;
  name: string;
  role: AgentRole;
  permissionProfile: PermissionProfile;
  provider: AiProvider;
  workspace: Workspace | null;
  model: string | null;
  mode: string | null;
}

export interface Execution {
  id: string;
  status: string;
  prompt: string;
  exitCode: number | null;
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

  listAgents: () => request<Agent[]>('/agents'),
  createAgent: (data: Record<string, unknown>) =>
    request<Agent>('/agents', { method: 'POST', body: JSON.stringify(data) }),

  createExecution: (data: { agentId: string; prompt: string }) =>
    request<Execution>('/executions', { method: 'POST', body: JSON.stringify(data) }),
  getExecution: (id: string) => request<Execution>(`/executions/${id}`),
};
