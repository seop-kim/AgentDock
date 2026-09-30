import { createContext, ReactNode, useContext, useMemo, useReducer, useRef } from 'react';
import { unavailableReason } from '../lib/agentAvailability';
import { attachmentPath } from '../lib/attachments';
import { buildExecutionPlan } from '../lib/executionSim';
import { formatCost, formatDuration } from '../lib/executions';
import type {
  Agent,
  AgentGroup,
  AiProvider,
  AttachedFile,
  Capabilities,
  ChatMessage,
  ChatTarget,
  Execution,
  Project,
  Task,
  Workspace,
} from '../types';
import {
  DEFAULT_CAPABILITIES,
  DEFAULT_PROVIDER_NAMES,
  SEED_AGENTS,
  SEED_EXECUTIONS,
  SEED_FILES,
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
  /** 워크스페이스 폴더 안의 파일 목록(모의). 밖에서 끌어온 파일은 `.agentdock/attachments` 로 복사해 여기 더한다. */
  workspaceFiles: Record<number, string[]>;
  projects: Project[];
  agents: Agent[];
  groups: AgentGroup[];
  tasks: Task[];
  /** 실행 트리. 마스터 실행이 루트이고, 위임할 때마다 자식 실행이 붙는다. */
  executions: Execution[];
  chats: ChatMessage[];
  /** 구성도에서 손으로 옮긴 노드 위치(월드 좌표). 없는 노드는 자동 배치를 따른다. */
  nodePositions: Record<number, { x: number; y: number }>;
  /** 손으로 옮긴 그룹 상자 위치(월드 좌표). 없는 그룹은 자동 배치를 따른다. */
  groupPositions: Record<number, { x: number; y: number }>;
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
  | { type: 'project/setMaster'; projectId: number; agentId: number }
  | { type: 'project/setMasterPrompt'; projectId: number; prompt: string }
  | { type: 'project/assignWorkspace'; projectId: number; workspaceId: number; asDefault: boolean }
  | { type: 'project/removeWorkspace'; projectId: number; workspaceId: number }
  | { type: 'agent/create'; input: NewAgentInput }
  | { type: 'agent/update'; id: number; input: AgentUpdateInput }
  | { type: 'agent/delete'; id: number }
  | { type: 'agent/setPlaced'; id: number; placed: boolean }
  | { type: 'layout/setPosition'; agentId: number; x: number; y: number }
  | { type: 'layout/setGroupPosition'; groupId: number; x: number; y: number }
  | { type: 'layout/clearPositions' }
  | { type: 'group/create'; projectId: number; name: string }
  | { type: 'group/delete'; id: number }
  | { type: 'group/addMember'; groupId: number; agentId: number }
  | { type: 'group/removeMember'; groupId: number; agentId: number }
  | { type: 'group/setLeader'; groupId: number; agentId: number | null }
  | { type: 'group/setPrompt'; groupId: number; prompt: string }
  | { type: 'chat/system'; message: ChatMessage }
  | { type: 'chat/send'; user: ChatMessage; pending: ChatMessage; tasks: Task[]; executions: Execution[] }
  | { type: 'chat/reply'; messageId: number; taskIds: number[]; text: string }
  | { type: 'execution/patch'; id: number; patch: Partial<Execution> }
  | { type: 'file/add'; workspaceId: number; name: string };

const initialState: MockState = {
  providers: SEED_PROVIDERS,
  workspaces: SEED_WORKSPACES,
  workspaceFiles: SEED_FILES,
  projects: SEED_PROJECTS,
  agents: SEED_AGENTS,
  groups: SEED_GROUPS,
  tasks: SEED_TASKS,
  executions: SEED_EXECUTIONS,
  chats: [],
  nodePositions: {},
  groupPositions: {},
  nextId: 100,
};

/** 손으로 옮긴 위치를 지운다(그룹에 들어가거나 구성도에서 빠지면 자동 배치로 돌아간다). */
const withoutPosition = (
  positions: Record<number, { x: number; y: number }>,
  agentId: number,
): Record<number, { x: number; y: number }> => {
  if (!(agentId in positions)) return positions;
  const next = { ...positions };
  delete next[agentId];
  return next;
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
      // 새 프로젝트는 마스터 미지정으로 시작한다(설정에서 지정해야 한다).
      return {
        ...state,
        projects: [
          ...state.projects,
          { id: state.nextId, name: action.name, workspaces: [], masterAgentId: null, masterPrompt: '' },
        ],
        nextId: state.nextId + 1,
      };
    case 'project/rename':
      return mapProject(state, action.id, (project) => ({ ...project, name: action.name }));
    case 'project/setMaster':
      // 마스터는 프로젝트 최상위 리더라 그룹에 속하지 않는다(지정하면 모든 그룹에서 뺀다).
      return {
        ...state,
        projects: state.projects.map((p) => (p.id === action.projectId ? { ...p, masterAgentId: action.agentId } : p)),
        groups: withoutMember(state.groups, action.agentId),
      };
    case 'project/setMasterPrompt':
      return mapProject(state, action.projectId, (project) => ({ ...project, masterPrompt: action.prompt }));
    case 'project/delete':
      // 프로젝트에 속한 에이전트, 그룹, Task, 실행 기록도 함께 사라진다.
      return {
        ...state,
        projects: state.projects.filter((p) => p.id !== action.id),
        agents: state.agents.filter((a) => a.projectId !== action.id),
        groups: state.groups.filter((g) => g.projectId !== action.id),
        tasks: state.tasks.filter((t) => t.projectId !== action.id),
        executions: state.executions.filter((e) => e.projectId !== action.id),
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
    case 'agent/delete': {
      // 마스터는 지울 수 없다(먼저 설정에서 다른 에이전트로 마스터를 바꿔야 한다).
      if (state.projects.some((p) => p.masterAgentId === action.id)) {
        return state;
      }
      // 에이전트가 사라지면 모든 그룹에서도 빠지고, 리더였다면 남은 첫 멤버가 이어받는다. 이미 만든 Task 는 남긴다.
      return {
        ...state,
        agents: state.agents.filter((a) => a.id !== action.id),
        groups: withoutMember(state.groups, action.id),
        nodePositions: withoutPosition(state.nodePositions, action.id),
      };
    }
    case 'agent/setPlaced': {
      // 마스터는 최상위 리더라 구성도에서 뺄 수 없다(항상 놓여 있다).
      if (!action.placed && state.projects.some((p) => p.masterAgentId === action.id)) {
        return state;
      }
      // 구성도에서 빼면 그룹에서도 빠진다(다시 놓으면 그룹 없이 노드만 놓인다).
      return {
        ...state,
        agents: state.agents.map((a) => (a.id === action.id ? { ...a, placed: action.placed } : a)),
        groups: action.placed ? state.groups : withoutMember(state.groups, action.id),
        nodePositions: action.placed ? state.nodePositions : withoutPosition(state.nodePositions, action.id),
      };
    }
    case 'layout/setPosition': {
      // 자유 위치는 그룹에 속하지 않은 노드에만 준다.
      if (state.groups.some((g) => g.memberIds.includes(action.agentId))) return state;
      return {
        ...state,
        nodePositions: { ...state.nodePositions, [action.agentId]: { x: action.x, y: action.y } },
      };
    }
    case 'layout/setGroupPosition':
      return {
        ...state,
        groupPositions: { ...state.groupPositions, [action.groupId]: { x: action.x, y: action.y } },
      };
    case 'layout/clearPositions':
      return { ...state, nodePositions: {}, groupPositions: {} };
    case 'group/create':
      return {
        ...state,
        groups: [
          ...state.groups,
          {
            id: state.nextId,
            projectId: action.projectId,
            name: action.name,
            leaderAgentId: null,
            memberIds: [],
            prompt: '',
          },
        ],
        nextId: state.nextId + 1,
      };
    case 'group/delete': {
      const groupPositions = { ...state.groupPositions };
      delete groupPositions[action.id];
      return { ...state, groups: state.groups.filter((g) => g.id !== action.id), groupPositions };
    }
    case 'group/addMember': {
      // 첫 멤버는 리더가 된다(리더가 없을 때만). 그룹에 들어가면 손으로 옮긴 위치는 지운다.
      const next = mapGroup(state, action.groupId, (group) =>
        group.memberIds.includes(action.agentId)
          ? group
          : {
              ...group,
              memberIds: [...group.memberIds, action.agentId],
              leaderAgentId: group.leaderAgentId ?? action.agentId,
            },
      );
      return { ...next, nodePositions: withoutPosition(next.nodePositions, action.agentId) };
    }
    case 'group/removeMember':
      // 리더를 빼면 남은 첫 멤버가 리더를 이어받는다.
      return mapGroup(state, action.groupId, (group) => {
        const memberIds = group.memberIds.filter((id) => id !== action.agentId);
        const leaderAgentId = group.leaderAgentId === action.agentId ? (memberIds[0] ?? null) : group.leaderAgentId;
        return { ...group, memberIds, leaderAgentId };
      });
    case 'group/setLeader':
      return mapGroup(state, action.groupId, (group) => ({ ...group, leaderAgentId: action.agentId }));
    case 'group/setPrompt':
      return mapGroup(state, action.groupId, (group) => ({ ...group, prompt: action.prompt }));
    case 'chat/system':
      return { ...state, chats: [...state.chats, action.message] };
    case 'chat/send':
      return {
        ...state,
        chats: [...state.chats, action.user, action.pending],
        tasks: [...state.tasks, ...action.tasks],
        executions: [...state.executions, ...action.executions],
      };
    case 'execution/patch':
      // 계획대로 상태를 바꾼다(자식이 도는 동안 부모는 WAITING_CHILD).
      return {
        ...state,
        executions: state.executions.map((e) => (e.id === action.id ? { ...e, ...action.patch } : e)),
      };
    case 'file/add': {
      // 밖에서 끌어온 파일을 프로젝트 폴더로 복사한 것으로 친다(같은 이름이면 덮어쓴 것으로 보고 한 번만 둔다).
      const path = attachmentPath(action.name);
      const files = state.workspaceFiles[action.workspaceId] ?? [];
      if (files.includes(path)) return state;
      return { ...state, workspaceFiles: { ...state.workspaceFiles, [action.workspaceId]: [...files, path] } };
    }
    case 'chat/reply':
      // 응답이 오면 대기 중이던 에이전트 메시지를 완료로 바꾸고, 이번 명령으로 만든 Task 를 모두 끝낸다.
      return {
        ...state,
        chats: state.chats.map((m) =>
          m.id === action.messageId ? { ...m, text: action.text, status: 'done' as const } : m,
        ),
        tasks: state.tasks.map((t) => (action.taskIds.includes(t.id) ? { ...t, status: 'DONE' as const } : t)),
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
  /** 워크스페이스 폴더 안의 파일 목록(모의). 첨부 창에서 고르는 목록이기도 하다. */
  workspaceFiles: Record<number, string[]>;
  /** 밖에서 끌어온 파일을 프로젝트 폴더(`.agentdock/attachments`)로 복사한 것으로 친다. */
  addWorkspaceFile: (workspaceId: number, name: string) => void;
  projects: Project[];
  agents: Agent[];
  groups: AgentGroup[];
  tasks: Task[];
  /** 실행 트리. 마스터 실행이 루트이고, 위임할 때마다 자식 실행이 붙는다. */
  executions: Execution[];
  createProject: (name: string) => void;
  renameProject: (id: number, name: string) => void;
  deleteProject: (id: number) => void;
  /** 프로젝트 마스터 에이전트 지정(변경). 지정하면 그 에이전트는 모든 그룹에서 빠진다. */
  setProjectMaster: (projectId: number, agentId: number) => void;
  setMasterPrompt: (projectId: number, prompt: string) => void;
  assignWorkspace: (projectId: number, workspaceId: number, asDefault: boolean) => void;
  removeWorkspace: (projectId: number, workspaceId: number) => void;
  createAgent: (input: NewAgentInput) => void;
  updateAgent: (id: number, input: AgentUpdateInput) => void;
  /** 마스터는 지울 수 없다(설정에서 다른 에이전트로 먼저 변경). */
  deleteAgent: (id: number) => void;
  /** 구성도에 놓기/빼기. 빼면 그룹에서도 빠지고 에이전트 목록에만 남는다. 마스터는 뺄 수 없다. */
  setAgentPlaced: (id: number, placed: boolean) => void;
  /** 구성도에서 손으로 옮긴 노드 위치(월드 좌표). 그룹에 속한 노드는 자동 배치를 따른다. */
  nodePositions: Record<number, { x: number; y: number }>;
  setAgentPosition: (agentId: number, x: number, y: number) => void;
  /** 손으로 옮긴 그룹 상자 위치(월드 좌표). */
  groupPositions: Record<number, { x: number; y: number }>;
  setGroupPosition: (groupId: number, x: number, y: number) => void;
  /** 손으로 옮긴 위치(노드·그룹)를 모두 지워 자동 배치로 되돌린다. */
  clearPositions: () => void;
  createGroup: (projectId: number, name: string) => void;
  deleteGroup: (id: number) => void;
  addGroupMember: (groupId: number, agentId: number) => void;
  removeGroupMember: (groupId: number, agentId: number) => void;
  setGroupLeader: (groupId: number, agentId: number | null) => void;
  setGroupPrompt: (groupId: number, prompt: string) => void;
  chats: ChatMessage[];
  /**
   * 에이전트나 그룹(리더)에게 명령을 보낸다. **마스터**에게 보내면 마스터가 팀(그룹) 리더들에게 나눠 맡긴다(모의).
   * 첨부한 파일은 라우팅(경로·확장자)과 실행 프롬프트에 함께 쓰인다.
   * 보낼 수 없으면 채팅에 사유가 시스템 메시지로 남는다.
   */
  sendCommand: (projectId: number, target: ChatTarget, text: string, attachments: AttachedFile[]) => void;
}

const MockStoreContext = createContext<MockStore | null>(null);

export function MockStoreProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);
  // 채팅 메시지/Task id. 응답 타이머가 나중에 같은 id 를 찾아야 하므로 렌더와 무관하게 증가시킨다.
  const seq = useRef(1000);
  const nextSeq = () => {
    seq.current += 1;
    return seq.current;
  };

  const sendCommand = (projectId: number, target: ChatTarget, text: string, attachments: AttachedFile[]) => {
    const project = state.projects.find((p) => p.id === projectId);
    const group = target.kind === 'group' ? state.groups.find((g) => g.id === target.id) : undefined;
    const receiverId = target.kind === 'agent' ? target.id : group?.leaderAgentId;
    const receiver = state.agents.find((a) => a.id === receiverId);
    const targetName = target.kind === 'agent' ? receiver?.name : group?.name;

    const system = (message: string) =>
      dispatch({
        type: 'chat/system',
        message: {
          id: nextSeq(),
          projectId,
          role: 'system',
          author: '시스템',
          text: message,
          status: 'error',
          rootExecutionId: null,
          attachments: [],
        },
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
    const targetLabel = target.kind === 'group' ? `${group?.name} → 리더 ${receiver.name}` : receiver.name;
    const title = text.length > 28 ? `${text.slice(0, 28)}…` : text;

    // 마스터에게 온 명령만 팀으로 나눠 맡긴다. 그룹/에이전트에게 온 명령은 받은 실행 하나(과 그 하위)로 끝난다.
    const isMaster = project?.masterAgentId === receiver.id;
    const plan = buildExecutionPlan({
      text,
      rootAgentId: receiver.id,
      fromMaster: isMaster,
      groups: state.groups.filter((g) => g.projectId === projectId),
      attachments,
    });

    // 계획된 실행을 먼저 만들어 두고(QUEUED) 타임라인대로 상태를 바꾼다. 부모가 자식보다 먼저 온다.
    const ids = new Map<string, number>();
    const executions: Execution[] = plan.nodes.map((node) => {
      const id = nextSeq();
      ids.set(node.key, id);
      return {
        id,
        projectId,
        agentId: node.agentId,
        parentExecutionId: node.parentKey === null ? null : (ids.get(node.parentKey) ?? null),
        status: 'QUEUED' as const,
        prompt: node.prompt,
        decision: null,
        handoff: null,
        metrics: null,
        sessionId: node.sessionId,
      };
    });
    const agentName = (id: number) => state.agents.find((a) => a.id === id)?.name ?? '에이전트';

    // 실행마다 Task 를 하나씩 만든다(에이전트 상태 표시가 이 Task 를 본다).
    const tasks: Task[] = executions.map((execution) => ({
      id: nextSeq(),
      projectId,
      title: execution.parentExecutionId === null ? title : `${agentName(execution.agentId)}: ${title}`,
      status: 'RUNNING' as const,
      agentId: execution.agentId,
    }));

    dispatch({
      type: 'chat/send',
      user: {
        id: userId,
        projectId,
        role: 'user',
        author: '나',
        text,
        targetLabel,
        status: 'done',
        rootExecutionId: null,
        attachments,
      },
      pending: {
        id: agentMessageId,
        projectId,
        role: 'agent',
        author: receiver.name,
        text: '',
        status: 'pending',
        rootExecutionId: ids.get('e0') ?? null,
        attachments: [],
      },
      tasks,
      executions,
    });

    // 계획대로 상태를 바꾼다(모의 재생). 자식이 도는 동안 부모는 WAITING_CHILD 로 멈춘다.
    plan.timeline.forEach((event) => {
      const id = ids.get(event.key);
      if (id === undefined) return;
      window.setTimeout(() => {
        dispatch({
          type: 'execution/patch',
          id,
          patch: {
            status: event.status,
            ...(event.decision === undefined ? {} : { decision: event.decision }),
            ...(event.handoff === undefined ? {} : { handoff: event.handoff }),
            ...(event.metrics === undefined ? {} : { metrics: event.metrics }),
          },
        });
      }, event.at);
    });

    // 마지막 상태 전이가 끝난 뒤에 응답을 남긴다. 숫자는 계획에서 나온 모의 값이다.
    const doneAt = plan.timeline.reduce((latest, event) => Math.max(latest, event.at), 0);
    const reply = isMaster
      ? plan.teamNames.length > 0
        ? `${plan.teamNames.join(' · ')} 에 나눠 맡기고 결과를 모았습니다. 실행 ${plan.executions}회 · 위임 ${plan.delegations}건 · ${formatCost(plan.costUsd)} · ${formatDuration(plan.durationMs)} (모의)`
        : `직접 처리했습니다. 실행 1회 · ${formatCost(plan.costUsd)} · ${formatDuration(plan.durationMs)} (모의)`
      : `요청을 확인했습니다. "${title}" 작업을 진행했고 완료했습니다. (모의)`;

    window.setTimeout(() => {
      dispatch({
        type: 'chat/reply',
        messageId: agentMessageId,
        taskIds: tasks.map((t) => t.id),
        text: reply,
      });
    }, doneAt + 300);
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
      workspaceFiles: state.workspaceFiles,
      addWorkspaceFile: (workspaceId, name) => dispatch({ type: 'file/add', workspaceId, name }),
      projects: state.projects,
      agents: state.agents,
      groups: state.groups,
      tasks: state.tasks,
      executions: state.executions,
      createProject: (name) => dispatch({ type: 'project/create', name }),
      renameProject: (id, name) => dispatch({ type: 'project/rename', id, name }),
      deleteProject: (id) => dispatch({ type: 'project/delete', id }),
      setProjectMaster: (projectId, agentId) => dispatch({ type: 'project/setMaster', projectId, agentId }),
      setMasterPrompt: (projectId, prompt) => dispatch({ type: 'project/setMasterPrompt', projectId, prompt }),
      assignWorkspace: (projectId, workspaceId, asDefault) =>
        dispatch({ type: 'project/assignWorkspace', projectId, workspaceId, asDefault }),
      removeWorkspace: (projectId, workspaceId) =>
        dispatch({ type: 'project/removeWorkspace', projectId, workspaceId }),
      createAgent: (input) => dispatch({ type: 'agent/create', input }),
      updateAgent: (id, input) => dispatch({ type: 'agent/update', id, input }),
      deleteAgent: (id) => dispatch({ type: 'agent/delete', id }),
      setAgentPlaced: (id, placed) => dispatch({ type: 'agent/setPlaced', id, placed }),
      nodePositions: state.nodePositions,
      setAgentPosition: (agentId, x, y) => dispatch({ type: 'layout/setPosition', agentId, x, y }),
      groupPositions: state.groupPositions,
      setGroupPosition: (groupId, x, y) => dispatch({ type: 'layout/setGroupPosition', groupId, x, y }),
      clearPositions: () => dispatch({ type: 'layout/clearPositions' }),
      createGroup: (projectId, name) => dispatch({ type: 'group/create', projectId, name }),
      deleteGroup: (id) => dispatch({ type: 'group/delete', id }),
      addGroupMember: (groupId, agentId) => dispatch({ type: 'group/addMember', groupId, agentId }),
      removeGroupMember: (groupId, agentId) => dispatch({ type: 'group/removeMember', groupId, agentId }),
      setGroupLeader: (groupId, agentId) => dispatch({ type: 'group/setLeader', groupId, agentId }),
      setGroupPrompt: (groupId, prompt) => dispatch({ type: 'group/setPrompt', groupId, prompt }),
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
