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
