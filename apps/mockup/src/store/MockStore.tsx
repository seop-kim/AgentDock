import { createContext, ReactNode, useContext, useMemo, useReducer } from 'react';
import type { Agent, AgentGroup, AiProvider, Capabilities, Project, Task, Workspace } from '../types';
import {
  DEFAULT_CAPABILITIES,
  DEFAULT_PROVIDER_NAMES,
  SEED_AGENTS,
  SEED_GROUPS,
  SEED_PROJECTS,
  SEED_PROVIDERS,
  SEED_TASKS,
  SEED_WORKSPACES,
} from './seed';

/** 목업의 메모리 상태. 백엔드/저장소 없이 새로고침하면 시드 값으로 돌아간다. */
interface MockState {
  providers: AiProvider[];
  workspaces: Workspace[];
  projects: Project[];
  agents: Agent[];
  groups: AgentGroup[];
  tasks: Task[];
  nextId: number;
}

export type NewAgentInput = Omit<Agent, 'id'>;

type Action =
  | { type: 'provider/toggle'; id: number }
  | { type: 'provider/delete'; id: number }
  | { type: 'provider/add'; key: string; name: string }
  | { type: 'provider/capabilities'; id: number; capabilities: Capabilities }
  | { type: 'project/create'; name: string }
  | { type: 'project/assignWorkspace'; projectId: number; workspaceId: number; asDefault: boolean }
  | { type: 'project/removeWorkspace'; projectId: number; workspaceId: number }
  | { type: 'agent/create'; input: NewAgentInput }
  | { type: 'group/create'; projectId: number; name: string }
  | { type: 'group/delete'; id: number }
  | { type: 'group/addMember'; groupId: number; agentId: number }
  | { type: 'group/removeMember'; groupId: number; agentId: number }
  | { type: 'group/setLeader'; groupId: number; agentId: number | null };

const initialState: MockState = {
  providers: SEED_PROVIDERS,
  workspaces: SEED_WORKSPACES,
  projects: SEED_PROJECTS,
  agents: SEED_AGENTS,
  groups: SEED_GROUPS,
  tasks: SEED_TASKS,
  nextId: 100,
};

const mapProject = (state: MockState, projectId: number, change: (project: Project) => Project): MockState => ({
  ...state,
  projects: state.projects.map((p) => (p.id === projectId ? change(p) : p)),
});

const mapGroup = (state: MockState, groupId: number, change: (group: AgentGroup) => AgentGroup): MockState => ({
  ...state,
  groups: state.groups.map((g) => (g.id === groupId ? change(g) : g)),
});

function reducer(state: MockState, action: Action): MockState {
  switch (action.type) {
    case 'provider/toggle':
      return {
        ...state,
        providers: state.providers.map((p) => (p.id === action.id ? { ...p, enabled: !p.enabled } : p)),
      };
    case 'provider/delete':
      return { ...state, providers: state.providers.filter((p) => p.id !== action.id) };
    case 'provider/add':
      return {
        ...state,
        providers: [
          ...state.providers,
          {
            id: state.nextId,
            key: action.key,
            name: action.name,
            enabled: false,
            capabilities: DEFAULT_CAPABILITIES[action.key],
          },
        ],
        nextId: state.nextId + 1,
      };
    case 'provider/capabilities':
      return {
        ...state,
        providers: state.providers.map((p) =>
          p.id === action.id ? { ...p, capabilities: action.capabilities } : p,
        ),
      };
    case 'project/create':
      return {
        ...state,
        projects: [...state.projects, { id: state.nextId, name: action.name, workspaces: [] }],
        nextId: state.nextId + 1,
      };
    case 'project/assignWorkspace':
      // 첫 할당은 자동으로 기본이 되고, "기본으로"를 고르면 기존 기본을 대체한다(프로젝트당 기본은 1개).
      return mapProject(state, action.projectId, (project) => {
        if (project.workspaces.some((w) => w.workspaceId === action.workspaceId)) return project;
        const makeDefault = action.asDefault || project.workspaces.length === 0;
        return {
          ...project,
          workspaces: [
            ...project.workspaces.map((w) => (makeDefault ? { ...w, isDefault: false } : w)),
            { workspaceId: action.workspaceId, isDefault: makeDefault },
          ],
        };
      });
    case 'project/removeWorkspace':
      // 기본을 해제하면 남은 첫 워크스페이스가 기본을 이어받는다.
      return mapProject(state, action.projectId, (project) => {
        const remaining = project.workspaces.filter((w) => w.workspaceId !== action.workspaceId);
        const hasDefault = remaining.some((w) => w.isDefault);
        return {
          ...project,
          workspaces: remaining.map((w, index) => (!hasDefault && index === 0 ? { ...w, isDefault: true } : w)),
        };
      });
    case 'agent/create':
      return {
        ...state,
        agents: [...state.agents, { ...action.input, id: state.nextId }],
        nextId: state.nextId + 1,
      };
    case 'group/create':
      return {
        ...state,
        groups: [
          ...state.groups,
          { id: state.nextId, projectId: action.projectId, name: action.name, leaderAgentId: null, memberIds: [] },
        ],
        nextId: state.nextId + 1,
      };
    case 'group/delete':
      return { ...state, groups: state.groups.filter((g) => g.id !== action.id) };
    case 'group/addMember':
      // 첫 멤버는 리더가 된다(리더가 없을 때만).
      return mapGroup(state, action.groupId, (group) =>
        group.memberIds.includes(action.agentId)
          ? group
          : {
              ...group,
              memberIds: [...group.memberIds, action.agentId],
              leaderAgentId: group.leaderAgentId ?? action.agentId,
            },
      );
    case 'group/removeMember':
      // 리더를 빼면 남은 첫 멤버가 리더를 이어받는다.
      return mapGroup(state, action.groupId, (group) => {
        const memberIds = group.memberIds.filter((id) => id !== action.agentId);
        const leaderAgentId = group.leaderAgentId === action.agentId ? (memberIds[0] ?? null) : group.leaderAgentId;
        return { ...group, memberIds, leaderAgentId };
      });
    case 'group/setLeader':
      return mapGroup(state, action.groupId, (group) => ({ ...group, leaderAgentId: action.agentId }));
  }
}

interface MockStore {
  providers: AiProvider[];
  /** 아직 등록되지 않은 런타임 종류(추가 모달의 선택지). */
  availableProviderKeys: string[];
  toggleProvider: (id: number) => void;
  deleteProvider: (id: number) => void;
  addProvider: (key: string, name: string) => void;
  updateCapabilities: (id: number, capabilities: Capabilities) => void;
  workspaces: Workspace[];
  projects: Project[];
  agents: Agent[];
  groups: AgentGroup[];
  tasks: Task[];
  createProject: (name: string) => void;
  assignWorkspace: (projectId: number, workspaceId: number, asDefault: boolean) => void;
  removeWorkspace: (projectId: number, workspaceId: number) => void;
  createAgent: (input: NewAgentInput) => void;
  createGroup: (projectId: number, name: string) => void;
  deleteGroup: (id: number) => void;
  addGroupMember: (groupId: number, agentId: number) => void;
  removeGroupMember: (groupId: number, agentId: number) => void;
  setGroupLeader: (groupId: number, agentId: number | null) => void;
}

const MockStoreContext = createContext<MockStore | null>(null);

export function MockStoreProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);

  const store = useMemo<MockStore>(
    () => ({
      providers: state.providers,
      availableProviderKeys: Object.keys(DEFAULT_PROVIDER_NAMES).filter(
        (key) => !state.providers.some((p) => p.key === key),
      ),
      toggleProvider: (id) => dispatch({ type: 'provider/toggle', id }),
      deleteProvider: (id) => dispatch({ type: 'provider/delete', id }),
      addProvider: (key, name) => dispatch({ type: 'provider/add', key, name }),
      updateCapabilities: (id, capabilities) => dispatch({ type: 'provider/capabilities', id, capabilities }),
      workspaces: state.workspaces,
      projects: state.projects,
      agents: state.agents,
      groups: state.groups,
      tasks: state.tasks,
      createProject: (name) => dispatch({ type: 'project/create', name }),
      assignWorkspace: (projectId, workspaceId, asDefault) =>
        dispatch({ type: 'project/assignWorkspace', projectId, workspaceId, asDefault }),
      removeWorkspace: (projectId, workspaceId) =>
        dispatch({ type: 'project/removeWorkspace', projectId, workspaceId }),
      createAgent: (input) => dispatch({ type: 'agent/create', input }),
      createGroup: (projectId, name) => dispatch({ type: 'group/create', projectId, name }),
      deleteGroup: (id) => dispatch({ type: 'group/delete', id }),
      addGroupMember: (groupId, agentId) => dispatch({ type: 'group/addMember', groupId, agentId }),
      removeGroupMember: (groupId, agentId) => dispatch({ type: 'group/removeMember', groupId, agentId }),
      setGroupLeader: (groupId, agentId) => dispatch({ type: 'group/setLeader', groupId, agentId }),
    }),
    [state],
  );

  return <MockStoreContext.Provider value={store}>{children}</MockStoreContext.Provider>;
}

export function useMockStore(): MockStore {
  const store = useContext(MockStoreContext);
  if (!store) throw new Error('useMockStore must be used inside MockStoreProvider');
  return store;
}
