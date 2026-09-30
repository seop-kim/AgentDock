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
}

export interface AgentGroup {
  id: number;
  projectId: number;
  name: string;
  leaderAgentId: number | null;
  /** 한 에이전트는 여러 그룹에 속할 수 있다. */
  memberIds: number[];
}

export type TaskStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';

export interface Task {
  id: number;
  projectId: number;
  title: string;
  status: TaskStatus;
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
}
