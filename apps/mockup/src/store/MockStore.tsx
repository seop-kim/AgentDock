import { createContext, ReactNode, useContext, useMemo, useReducer, useRef } from 'react';
import { unavailableReason } from '../lib/agentAvailability';
import type {
  Agent,
  AgentGroup,
  AiProvider,
  Capabilities,
  ChatMessage,
  ChatTarget,
  Project,
  Task,
  Workspace,
} from '../types';
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
  chats: ChatMessage[];
  nextId: number;
}

/** 생성 입력: 새 에이전트는 구성도에 놓인 상태로 시작하므로 배치 여부는 폼에서 받지 않는다. */
export type NewAgentInput = Omit<Agent, 'id' | 'placed'>;
/** 에이전트 수정 입력: 소속 프로젝트와 배치 여부는 바꾸지 않는다(배치는 구성도/목록에서 직접). */
export type AgentUpdateInput = Omit<Agent, 'id' | 'projectId' | 'placed'>;

type Action =
  | { type: 'provider/toggle'; id: number }
  | { type: 'provider/delete'; id: number }
  | { type: 'provider/add'; key: string; name: string }
  | { type: 'provider/capabilities'; id: number; capabilities: Capabilities }
  | { type: 'project/create'; name: string }
  | { type: 'project/rename'; id: number; name: string }
  | { type: 'project/delete'; id: number }
  | { type: 'project/assignWorkspace'; projectId: number; workspaceId: number; asDefault: boolean }
  | { type: 'project/removeWorkspace'; projectId: number; workspaceId: number }
  | { type: 'agent/create'; input: NewAgentInput }
  | { type: 'agent/update'; id: number; input: AgentUpdateInput }
  | { type: 'agent/delete'; id: number }
  | { type: 'agent/setPlaced'; id: number; placed: boolean }
  | { type: 'group/create'; projectId: number; name: string }
  | { type: 'group/delete'; id: number }
  | { type: 'group/addMember'; groupId: number; agentId: number }
  | { type: 'group/removeMember'; groupId: number; agentId: number }
  | { type: 'group/setLeader'; groupId: number; agentId: number | null }
  | { type: 'chat/system'; message: ChatMessage }
  | { type: 'chat/send'; user: ChatMessage; pending: ChatMessage; task: Task }
  | { type: 'chat/reply'; messageId: number; taskId: number; text: string };

const initialState: MockState = {
  providers: SEED_PROVIDERS,
  workspaces: SEED_WORKSPACES,
  projects: SEED_PROJECTS,
  agents: SEED_AGENTS,
  groups: SEED_GROUPS,
  tasks: SEED_TASKS,
  chats: [],
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

/** 그룹 목록에서 에이전트를 뺀다. 리더였다면 남은 첫 멤버가 리더를 이어받는다. */
const withoutMember = (groups: AgentGroup[], agentId: number): AgentGroup[] =>
  groups.map((group) => {
    if (!group.memberIds.includes(agentId)) return group;
    const memberIds = group.memberIds.filter((id) => id !== agentId);
    const leaderAgentId = group.leaderAgentId === agentId ? (memberIds[0] ?? null) : group.leaderAgentId;
    return { ...group, memberIds, leaderAgentId };
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
    case 'project/rename':
      return mapProject(state, action.id, (project) => ({ ...project, name: action.name }));
    case 'project/delete':
      // 프로젝트에 속한 에이전트, 그룹, Task 도 함께 사라진다.
      return {
        ...state,
        projects: state.projects.filter((p) => p.id !== action.id),
        agents: state.agents.filter((a) => a.projectId !== action.id),
        groups: state.groups.filter((g) => g.projectId !== action.id),
        tasks: state.tasks.filter((t) => t.projectId !== action.id),
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
      // 새 에이전트는 구성도에 놓인 상태로 시작한다.
      return {
        ...state,
        agents: [...state.agents, { ...action.input, id: state.nextId, placed: true }],
        nextId: state.nextId + 1,
      };
    case 'agent/update':
      return {
        ...state,
        agents: state.agents.map((a) => (a.id === action.id ? { ...a, ...action.input } : a)),
      };
    case 'agent/delete':
      // 에이전트가 사라지면 모든 그룹에서도 빠지고, 리더였다면 남은 첫 멤버가 이어받는다. 이미 만든 Task 는 남긴다.
      return {
        ...state,
        agents: state.agents.filter((a) => a.id !== action.id),
        groups: withoutMember(state.groups, action.id),
      };
    case 'agent/setPlaced':
      // 구성도에서 빼면 그룹에서도 빠진다(다시 놓으면 그룹 없이 노드만 놓인다).
      return {
        ...state,
        agents: state.agents.map((a) => (a.id === action.id ? { ...a, placed: action.placed } : a)),
        groups: action.placed ? state.groups : withoutMember(state.groups, action.id),
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
    case 'chat/system':
      return { ...state, chats: [...state.chats, action.message] };
    case 'chat/send':
      return {
        ...state,
        chats: [...state.chats, action.user, action.pending],
        tasks: [...state.tasks, action.task],
      };
    case 'chat/reply':
      // 응답이 오면 대기 중이던 에이전트 메시지를 완료로 바꾸고 Task 도 끝낸다.
      return {
        ...state,
        chats: state.chats.map((m) =>
          m.id === action.messageId ? { ...m, text: action.text, status: 'done' as const } : m,
        ),
        tasks: state.tasks.map((t) => (t.id === action.taskId ? { ...t, status: 'DONE' as const } : t)),
      };
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
  renameProject: (id: number, name: string) => void;
  deleteProject: (id: number) => void;
  assignWorkspace: (projectId: number, workspaceId: number, asDefault: boolean) => void;
  removeWorkspace: (projectId: number, workspaceId: number) => void;
  createAgent: (input: NewAgentInput) => void;
  updateAgent: (id: number, input: AgentUpdateInput) => void;
  deleteAgent: (id: number) => void;
  /** 구성도에 놓기/빼기. 빼면 그룹에서도 빠지고 에이전트 목록에만 남는다. */
  setAgentPlaced: (id: number, placed: boolean) => void;
  createGroup: (projectId: number, name: string) => void;
  deleteGroup: (id: number) => void;
  addGroupMember: (groupId: number, agentId: number) => void;
  removeGroupMember: (groupId: number, agentId: number) => void;
  setGroupLeader: (groupId: number, agentId: number | null) => void;
  chats: ChatMessage[];
  /** 에이전트나 그룹(리더)에게 명령을 보낸다. 보낼 수 없으면 채팅에 사유가 시스템 메시지로 남는다. */
  sendCommand: (projectId: number, target: ChatTarget, text: string) => void;
}

const REPLY_DELAY_MS = 1800;

const MockStoreContext = createContext<MockStore | null>(null);

export function MockStoreProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);
  // 채팅 메시지/Task id. 응답 타이머가 나중에 같은 id 를 찾아야 하므로 렌더와 무관하게 증가시킨다.
  const seq = useRef(1000);
  const nextSeq = () => {
    seq.current += 1;
    return seq.current;
  };

  const sendCommand = (projectId: number, target: ChatTarget, text: string) => {
    const project = state.projects.find((p) => p.id === projectId);
    const group = target.kind === 'group' ? state.groups.find((g) => g.id === target.id) : undefined;
    const receiverId = target.kind === 'agent' ? target.id : group?.leaderAgentId;
    const receiver = state.agents.find((a) => a.id === receiverId);
    const targetName = target.kind === 'agent' ? receiver?.name : group?.name;

    const system = (message: string) =>
      dispatch({
        type: 'chat/system',
        message: { id: nextSeq(), projectId, role: 'system', author: '시스템', text: message, status: 'error' },
      });

    if (target.kind === 'group' && !receiver) {
      system(`${targetName ?? '그룹'} 에는 리더가 없어 명령을 보낼 수 없습니다. 멤버를 넣거나 리더를 지정하세요.`);
      return;
    }
    if (!receiver) {
      system('받는 에이전트를 찾을 수 없습니다.');
      return;
    }
    const reason = unavailableReason(receiver, state.providers, project);
    if (reason) {
      system(`${receiver.name} 은(는) 지금 실행할 수 없습니다: ${reason}`);
      return;
    }

    const userId = nextSeq();
    const agentMessageId = nextSeq();
    const taskId = nextSeq();
    const targetLabel = target.kind === 'group' ? `${group?.name} → 리더 ${receiver.name}` : receiver.name;
    const title = text.length > 28 ? `${text.slice(0, 28)}…` : text;

    dispatch({
      type: 'chat/send',
      user: { id: userId, projectId, role: 'user', author: '나', text, targetLabel, status: 'done' },
      pending: { id: agentMessageId, projectId, role: 'agent', author: receiver.name, text: '', status: 'pending' },
      task: { id: taskId, projectId, title, status: 'RUNNING', agentId: receiver.id },
    });

    window.setTimeout(() => {
      dispatch({
        type: 'chat/reply',
        messageId: agentMessageId,
        taskId,
        text: `요청을 확인했습니다. "${title}" 작업을 진행했고 완료했습니다. (모의 응답)`,
      });
    }, REPLY_DELAY_MS);
  };

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
      renameProject: (id, name) => dispatch({ type: 'project/rename', id, name }),
      deleteProject: (id) => dispatch({ type: 'project/delete', id }),
      assignWorkspace: (projectId, workspaceId, asDefault) =>
        dispatch({ type: 'project/assignWorkspace', projectId, workspaceId, asDefault }),
      removeWorkspace: (projectId, workspaceId) =>
        dispatch({ type: 'project/removeWorkspace', projectId, workspaceId }),
      createAgent: (input) => dispatch({ type: 'agent/create', input }),
      updateAgent: (id, input) => dispatch({ type: 'agent/update', id, input }),
      deleteAgent: (id) => dispatch({ type: 'agent/delete', id }),
      setAgentPlaced: (id, placed) => dispatch({ type: 'agent/setPlaced', id, placed }),
      createGroup: (projectId, name) => dispatch({ type: 'group/create', projectId, name }),
      deleteGroup: (id) => dispatch({ type: 'group/delete', id }),
      addGroupMember: (groupId, agentId) => dispatch({ type: 'group/addMember', groupId, agentId }),
      removeGroupMember: (groupId, agentId) => dispatch({ type: 'group/removeMember', groupId, agentId }),
      setGroupLeader: (groupId, agentId) => dispatch({ type: 'group/setLeader', groupId, agentId }),
      chats: state.chats,
      sendCommand,
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
