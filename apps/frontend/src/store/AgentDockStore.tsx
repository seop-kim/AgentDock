import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { toast } from '../components/Toaster';
import {
  Attachment as ApiAttachment,
  Execution as ApiExecution,
  ExecutionTree,
  api,
  type Agent as ApiAgent,
  type AgentGroup as ApiGroup,
  type Project as ApiProject,
  type Provider as ApiProvider,
  type Task as ApiTask,
} from '../lib/api';
import { formatDuration, workLog } from '../lib/executions';
import { DEFAULT_PROVIDER_NAMES } from './seed';
import type {
  Agent,
  AgentGroup,
  AttachedFile,
  ChatMessage,
  ChatTarget,
  Execution,
  Project,
  Provider,
  Task,
  Workspace,
} from '../types';

/** 채팅(명령) 한 페이지 크기. 이만큼만 태스크를 읽으므로 트리도 이만큼만 읽힌다(채팅 5 + 트리 5 = 10). */
const CHAT_PAGE_SIZE = 5;

export type NewAgentInput = Omit<Agent, 'id' | 'placed'>;
export type AgentUpdateInput = Omit<Agent, 'id' | 'projectId' | 'placed'>;

type Position = { x: number; y: number };

interface AgentDockStore {
  loading: boolean;
  error: string | null;
  /** 전역 이벤트 스트림(SSE)이 붙어 있는지. 끊긴 동안에는 폴링이 빨라진다(30초 → 5초). */
  streamConnected: boolean;

  providers: Provider[];
  availableProviderKeys: string[];
  workspaces: Workspace[];
  workspaceFiles: Record<number, string[]>;
  projects: Project[];
  agents: Agent[];
  groups: AgentGroup[];
  tasks: Task[];
  executions: Execution[];
  chats: ChatMessage[];
  roles: AgentRole[];
  permissionProfiles: PermissionProfile[];
  nodePositions: Record<number, Position>;
  groupPositions: Record<number, Position>;

  reload: () => Promise<void>;
  /** 프로젝트별로 아직 안 읽은(더 오래된) 명령이 남아 있는지. */
  chatHasMore: Record<number, boolean>;
  /** 이전 명령을 한 페이지(5개) 더 읽는다. */
  loadMoreChats: (projectId: number) => void;
  /** 실행 트리 창을 열어 둔 트리를 알린다(닫으면 null). 열어 둔 트리는 계속 다시 읽는다. */
  watchExecutionTree: (projectId: number, rootExecutionId: number | null) => void;

  toggleProvider: (id: number) => void;
  deleteProvider: (id: number) => void;
  addProvider: (key: string, name: string) => void;
  updateCapabilities: (id: number, capabilities: Record<string, unknown>) => void;

  createProject: (name: string) => void;
  renameProject: (id: number, name: string) => void;
  deleteProject: (id: number) => void;
  setProjectMaster: (projectId: number, agentId: number | null) => void;
  setMasterPrompt: (projectId: number, prompt: string) => void;
  assignWorkspace: (projectId: number, workspaceId: number, asDefault: boolean) => void;
  removeWorkspace: (projectId: number, workspaceId: number) => void;
  registerWorkspace: (name: string, path: string) => Promise<Workspace | null>;

  createAgent: (input: NewAgentInput) => void;
  updateAgent: (id: number, input: AgentUpdateInput) => void;
  deleteAgent: (id: number) => void;
  setAgentPlaced: (id: number, placed: boolean) => void;
  setAgentPosition: (agentId: number, x: number, y: number) => void;
  setGroupPosition: (groupId: number, x: number, y: number) => void;
  clearPositions: () => void;

  createGroup: (projectId: number, name: string) => void;
  deleteGroup: (id: number) => void;
  addGroupMember: (groupId: number, agentId: number) => void;
  removeGroupMember: (groupId: number, agentId: number) => void;
  setGroupLeader: (groupId: number, agentId: number | null) => void;
  setGroupPrompt: (groupId: number, prompt: string) => void;
  /** 그룹 공유 노트(그룹 안 실행들이 함께 보는 맥락). 사람이 고쳐 저장한다. */
  setGroupNote: (groupId: number, sharedNote: string) => void;

  sendCommand: (projectId: number, target: ChatTarget, text: string, attachments: AttachedFile[]) => void;
  /** 실행 트리의 워크트리를 정리한다(사람이 판단해 부른다). deleteBranch 면 전용 브랜치도 지운다. */
  removeWorktree: (executionId: number, deleteBranch: boolean) => void;
  /** 자동 병합이 MANUAL 로 끝난 트리를 다시 병합한다(사람이 변경을 정리한 뒤 부른다). */
  retryMerge: (executionId: number) => void;
  /** `WAITING_INPUT` 인 실행에 사람의 답을 넣고 그 실행을 이어서 돌린다. */
  answerExecution: (executionId: number, text: string) => void;
  uploadAttachments: (workspaceId: number, files: File[]) => Promise<AttachedFile[]>;
  /** 파일 목록은 필요할 때만 읽는다(첨부 창이 열릴 때). */
  loadWorkspaceFiles: (workspaceId: number) => void;
}

export interface AgentRole {
  id: number;
  name: string;
}

export interface PermissionProfile {
  id: number;
  name: string;
}

const StoreContext = createContext<AgentDockStore | null>(null);

/** 이벤트가 몰려 올 때 한 번에 처리하려고 모으는 시간(ms). */
const EVENT_DEBOUNCE_MS = 250;
/** 안전망 폴링 주기(ms). 평소에는 이벤트가 갱신하고, 이 주기는 놓친 것만 줍는다. */
const SAFETY_POLL_MS = 30000;
/** 스트림이 끊겨 있는 동안의 폴링 주기(ms). */
const DISCONNECTED_POLL_MS = 5000;

/**
 * 전역 이벤트 스트림(`GET /events/stream`)이 실어 오는 알림.
 * **값이 아니라 알림**이다 — 화면은 이걸 받아 바뀐 조각만 REST 로 다시 읽는다(모르는 필드는 null).
 */
interface DataChanged {
  type: string;
  projectId: number | null;
  executionId: number | null;
  taskId: number | null;
}

const text = (value: string | null | undefined) => value ?? '';

/** 결과 텍스트(계약 JSON)에서 요약을 꺼낸다. */
export function summaryOf(resultText: string | null | undefined): string {
  if (!resultText) return '';
  const start = resultText.indexOf('{');
  const end = resultText.lastIndexOf('}');
  if (start >= 0 && end > start) {
    try {
      const parsed = JSON.parse(resultText.slice(start, end + 1)) as { summary?: unknown };
      if (typeof parsed.summary === 'string') return parsed.summary;
    } catch {
      // JSON 이 아니면 원문을 쓴다.
    }
  }
  return resultText;
}

export function changedFilesOf(resultText: string | null | undefined): string[] {
  if (!resultText) return [];
  const start = resultText.indexOf('{');
  const end = resultText.lastIndexOf('}');
  if (start < 0 || end <= start) return [];
  try {
    const parsed = JSON.parse(resultText.slice(start, end + 1)) as { changedFiles?: unknown };
    return Array.isArray(parsed.changedFiles) ? parsed.changedFiles.map(String) : [];
  } catch {
    return [];
  }
}

/** 백엔드 실행 상태 → 목업이 쓰는 상태 이름. */
function executionStatus(status: string): Execution['status'] {
  switch (status) {
    case 'PENDING':
      return 'QUEUED';
    case 'SUCCEEDED':
      return 'DONE';
    case 'RUNNING':
    case 'WAITING_CHILD':
    case 'WAITING_INPUT':
    case 'FAILED':
    case 'CANCELLED':
      return status;
    default:
      return 'QUEUED';
  }
}

/** 백엔드 태스크 상태 → 목업이 쓰는 상태 이름. */
function taskStatus(status: string): Task['status'] {
  switch (status) {
    case 'CREATED':
      return 'PENDING';
    case 'IN_PROGRESS':
      return 'RUNNING';
    case 'SUCCEEDED':
      return 'DONE';
    case 'FAILED':
      return 'FAILED';
    default:
      return 'PENDING';
  }
}

function executionOf(execution: ApiExecution, projectId: number): Execution {
  const summary = summaryOf(execution.resultText);
  const changedFiles = changedFilesOf(execution.resultText);
  const decision: Execution['decision'] =
    execution.decision === 'DONE'
      ? { action: 'done', summary }
      : execution.decision === 'DELEGATE' && execution.delegatedTargetAgentId !== null
        ? { action: 'delegate', targetAgentId: execution.delegatedTargetAgentId, prompt: '' }
        : execution.decision === 'ASK'
          ? { action: 'ask', question: execution.question ?? '' }
          : null;
  return {
    id: execution.id,
    projectId,
    agentId: execution.agentId,
    parentExecutionId: execution.parentExecutionId,
    status: executionStatus(execution.status),
    prompt: execution.prompt,
    decision,
    handoff:
      execution.resultText === null || execution.resultText === undefined
        ? null
        : { summary, changedFiles },
    metrics:
      execution.inputTokens === null && execution.costUsd === null
        ? null
        : {
            inputTokens: execution.inputTokens ?? 0,
            outputTokens: execution.outputTokens ?? 0,
            costUsd: execution.costUsd ?? 0,
            durationMs: execution.durationMs ?? 0,
          },
    sessionId: text(execution.sessionId),
    worktreePath: execution.worktreePath ?? null,
    worktreeBranch: execution.worktreeBranch ?? null,
    resultCommit: execution.resultCommit ?? null,
    mergeStatus: execution.mergeStatus ?? null,
    mergeDetail: execution.mergeDetail ?? null,
    changedFiles: execution.changedFiles ?? [],
    question: execution.question ?? null,
    options: execution.questionOptions ?? [],
    answer: execution.answer ?? null,
  };
}

/** 끝나지 않은 실행인지(대기·실행 중·하위 대기·사람 입력 대기). */
const unfinished = (status: Execution['status']) =>
  status !== 'DONE' && status !== 'FAILED' && status !== 'CANCELLED';

function mapProvider(provider: ApiProvider): Provider {
  return {
    id: provider.id,
    key: provider.key,
    name: provider.name,
    enabled: provider.enabled,
    capabilities: {
      models: provider.capabilities?.models ?? [],
      modes: provider.capabilities?.modes ?? [],
      notes: provider.capabilities?.notes ?? null,
      install: provider.capabilities?.install ?? [],
      installRequire: provider.capabilities?.installRequire ?? null,
      installPrerequisite: provider.capabilities?.installPrerequisite ?? [],
    },
  };
}

function mapProject(project: ApiProject): Project {
  return {
    id: project.id,
    name: project.name,
    workspaces: project.workspaces.map((workspace) => ({
      workspaceId: workspace.workspaceId,
      isDefault: workspace.isDefault,
    })),
    masterAgentId: project.masterAgent?.id ?? null,
    masterPrompt: text(project.masterPrompt),
  };
}

function mapAgent(agent: ApiAgent): Agent {
  return {
    id: agent.id,
    projectId: agent.projectId,
    name: agent.name,
    roleId: agent.role?.id ?? 0,
    permissionProfileId: agent.permissionProfile?.id ?? 0,
    providerId: agent.providerId,
    persona: text(agent.persona),
    model: text(agent.model),
    mode: text(agent.mode),
    placed: agent.placed ?? true,
    available: agent.available,
    unavailableReason: agent.unavailableReason,
  };
}

function mapGroup(group: ApiGroup): AgentGroup {
  return {
    id: group.id,
    projectId: group.projectId,
    name: group.name,
    leaderAgentId: group.leader?.id ?? null,
    memberIds: group.members.map((member) => member.id),
    prompt: text(group.prompt),
    sharedNote: text(group.sharedNote),
  };
}

function mapTask(projectId: number, task: ApiTask): Task {
  return {
    id: task.id,
    projectId,
    title: task.title,
    status: taskStatus(task.status),
    agentId: task.agent?.id ?? null,
  };
}

/** 좌표가 있는 에이전트만 골라낸다(구성도 배치). */
function positionsOfAgents(rows: ApiAgent[]): Record<number, Position> {
  const positions: Record<number, Position> = {};
  rows.forEach((agent) => {
    if (agent.nodeX !== null && agent.nodeX !== undefined && agent.nodeY !== null && agent.nodeY !== undefined) {
      positions[agent.id] = { x: agent.nodeX, y: agent.nodeY };
    }
  });
  return positions;
}

/**
 * 다시 읽은 트리만 새 값으로 갈아 끼운다. 나머지 실행(다른 트리·다른 프로젝트)은 그대로 둔다 —
 * 자식 실행은 `parentExecutionId` 로 따라가며 함께 지운다(한 트리 안의 노드는 통째로 새로 읽는다).
 */
function mergeExecutions(prev: Execution[], roots: Set<number>, fresh: Execution[]): Execution[] {
  const dropped = new Set<number>();
  let grew = true;
  while (grew) {
    grew = false;
    prev.forEach((execution) => {
      if (dropped.has(execution.id)) return;
      if (
        roots.has(execution.id) ||
        (execution.parentExecutionId !== null && dropped.has(execution.parentExecutionId))
      ) {
        dropped.add(execution.id);
        grew = true;
      }
    });
  }
  return [...prev.filter((execution) => !dropped.has(execution.id)), ...fresh];
}

export function AgentDockStoreProvider({ children }: { children: ReactNode }) {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [workspaceFiles, setWorkspaceFiles] = useState<Record<number, string[]>>({});
  const [projects, setProjects] = useState<Project[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [groups, setGroups] = useState<AgentGroup[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [executions, setExecutions] = useState<Execution[]>([]);
  const [chats, setChats] = useState<ChatMessage[]>([]);
  const [roles, setRoles] = useState<AgentRole[]>([]);
  const [permissionProfiles, setPermissionProfiles] = useState<PermissionProfile[]>([]);
  const [nodePositions, setNodePositions] = useState<Record<number, Position>>({});
  const [groupPositions, setGroupPositions] = useState<Record<number, Position>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [streamConnected, setStreamConnected] = useState(false);

  const seqRef = useRef(1000);
  const nextId = useCallback(() => {
    seqRef.current += 1;
    return seqRef.current;
  }, []);

  const fail = useCallback((ex: unknown) => setError(String(ex)), []);

  // 조각을 다시 읽을 때 "이미 아는 것"을 판단해야 한다 — 콜백이 낡은 상태를 붙들지 않게 최신값을 ref 로도 들고 있는다.
  const executionsRef = useRef<Execution[]>([]);
  const agentsRef = useRef<ApiAgent[]>([]);
  const groupsRef = useRef<AgentGroup[]>([]);
  const filesRef = useRef<Record<number, string[]>>({});
  /** 태스크 → 최신 실행(트리 루트). 이벤트로 태스크 목록만 다시 읽을 때 트리의 뿌리를 알려면 필요하다. */
  const taskRootsRef = useRef<Map<number, number | null>>(new Map());
  /** 채팅(명령) 페이지 크기. '이전 명령 더 보기' 로 늘어난다 — 콜백에서 최신 값을 읽으려고 ref 로도 둔다. */
  const chatPageSizeRef = useRef(CHAT_PAGE_SIZE);
  const [chatHasMore, setChatHasMore] = useState<Record<number, boolean>>({});
  /** 실행 트리 창을 열어 둔 트리(프로젝트 → 루트 실행). 그 트리만 계속 다시 읽는다. */
  const openTreesRef = useRef<Map<number, Set<number>>>(new Map());
  /** 스트림이 알려 준 변화(디바운스로 모았다가 한 번에 처리한다). */
  const pendingRef = useRef<Map<string, DataChanged>>(new Map());
  const flushRef = useRef<number | null>(null);
  const reloadRef = useRef<() => Promise<void>>(async () => {});
  const applyEventRef = useRef<(event: DataChanged) => Promise<void>>(async () => {});

  /**
   * 작업 알림: 실행 상태가 **바뀔 때** 토스트로 알린다(시작·완료·실패·취소·질문 대기).
   * 사용자가 화면을 안 보고 있어도 무슨 일이 일어나는지 알 수 있게 한다.
   */
  const prevStatusRef = useRef<Map<number, Execution['status']>>(new Map());
  useEffect(() => {
    const previous = prevStatusRef.current;
    const next = new Map<number, Execution['status']>();
    executions.forEach((execution) => {
      next.set(execution.id, execution.status);
      const before = previous.get(execution.id);
      if (before === undefined || before === execution.status) return;
      const who = agentsRef.current.find((agent) => agent.id === execution.agentId)?.name ?? '에이전트';
      const label = `#${execution.id} ${who}`;
      if (execution.status === 'RUNNING') {
        toast(`${label} 작업을 시작했습니다`);
      } else if (execution.status === 'DONE') {
        const ms = execution.metrics?.durationMs ?? 0;
        toast(`${label} 작업 완료${ms > 0 ? ` (${formatDuration(ms)})` : ''}`, 'success');
      } else if (execution.status === 'FAILED') {
        toast(`${label} 작업이 실패했습니다`, 'error');
      } else if (execution.status === 'CANCELLED') {
        toast(`${label} 작업이 취소되었습니다`);
      } else if (execution.status === 'WAITING_INPUT') {
        toast(`${label} 답을 기다립니다 — 요청 창에서 답해 주세요`);
      }
    });
    prevStatusRef.current = next;
  }, [executions]);

  useEffect(() => {
    executionsRef.current = executions;
  }, [executions]);
  useEffect(() => {
    groupsRef.current = groups;
  }, [groups]);
  useEffect(() => {
    filesRef.current = workspaceFiles;
  }, [workspaceFiles]);

  /** AI 런타임 목록. */
  const loadProviders = useCallback(async () => {
    const rows = await api.listProviders();
    setProviders(rows.map(mapProvider));
  }, []);

  /** 워크스페이스 목록. **파일 목록은 여기서 읽지 않는다** — 필요할 때만(첨부 창) 읽는다. */
  const loadWorkspaces = useCallback(async () => {
    const rows = await api.listWorkspaces();
    setWorkspaces(rows.map((workspace) => ({ id: workspace.id, name: workspace.name, path: workspace.path })));
  }, []);

  /** 역할·권한 프로필(거의 변하지 않는다 — 전체 새로 읽기에서만 부른다). */
  const loadCatalog = useCallback(async () => {
    const [roleList, profileList] = await Promise.all([api.listRoles(), api.listPermissionProfiles()]);
    setRoles(roleList.map((role) => ({ id: role.id, name: role.name })));
    setPermissionProfiles(profileList.map((profile) => ({ id: profile.id, name: profile.name })));
  }, []);

  /** 프로젝트 목록(이름·마스터·워크스페이스 할당). 읽은 목록을 돌려줘 호출부가 이어 쓸 수 있게 한다. */
  const loadProjects = useCallback(async (): Promise<Project[]> => {
    const rows = await api.listProjects();
    const mapped = rows.map(mapProject);
    setProjects(mapped);
    return mapped;
  }, []);

  /** 에이전트 목록(전역)과 구성도 좌표. */
  const loadAgents = useCallback(async () => {
    const rows = await api.listAgents();
    agentsRef.current = rows;
    setAgents(rows.map(mapAgent));
    setNodePositions(positionsOfAgents(rows));
  }, []);

  /** 한 프로젝트의 그룹 목록. 다른 프로젝트의 그룹·좌표는 그대로 둔다. */
  const loadGroups = useCallback(async (projectId: number) => {
    const rows = await api.listGroups(projectId);
    const previousIds = new Set(
      groupsRef.current.filter((group) => group.projectId === projectId).map((group) => group.id),
    );
    setGroups((prev) => [...prev.filter((group) => group.projectId !== projectId), ...rows.map(mapGroup)]);
    setGroupPositions((prev) => {
      const next: Record<number, Position> = {};
      Object.entries(prev).forEach(([key, position]) => {
        if (!previousIds.has(Number(key))) next[Number(key)] = position;
      });
      rows.forEach((group) => {
        if (group.nodeX !== null && group.nodeX !== undefined && group.nodeY !== null && group.nodeY !== undefined) {
          next[group.id] = { x: group.nodeX, y: group.nodeY };
        }
      });
      return next;
    });
  }, []);

  /** 한 실행 트리를 읽어 화면 모양으로 바꾼다(못 읽으면 빈 배열 — 실행이 지워졌을 수 있다). */
  const fetchTree = useCallback(async (rootExecutionId: number, projectId: number): Promise<Execution[]> => {
    try {
      const tree: ExecutionTree = await api.getExecutionTree(rootExecutionId);
      return tree.nodes.map((node) => executionOf(node.execution, projectId));
    } catch {
      return [];
    }
  }, []);

  /**
   * 한 프로젝트의 채팅 기록을 서버 상태(태스크 + 실행 트리)에서 다시 만든다.
   * 명령 하나 = 내 말풍선 + 그 응답이고, 응답 문구·상태는 트리 루트의 상태·요약에서 온다(위가 옛날, 아래가 최신).
   */
  const rebuildChats = useCallback((projectId: number, taskList: Task[], executionList: Execution[]) => {
    const nameOf = (agentId: number | null) =>
      agentsRef.current.find((agent) => agent.id === agentId)?.name ?? '에이전트';
    const messages: ChatMessage[] = [];
    [...taskList].sort((left, right) => left.id - right.id).forEach((task) => {
      messages.push({
        id: task.id,
        projectId,
        role: 'user',
        author: '나',
        text: task.title,
        status: 'done',
        rootExecutionId: taskRootsRef.current.get(task.id) ?? null,
        attachments: [],
      });
      const rootId = taskRootsRef.current.get(task.id) ?? null;
      if (rootId === null) return;
      const candidate = executionList.find((execution) => execution.id === rootId);
      if (candidate === undefined) {
        // 트리를 아직 못 읽었어도 **지금 도는 작업**은 응답을 기다리는 중으로 보여 준다(다음 갱신에서 채워진다).
        if (task.status === 'PENDING' || task.status === 'RUNNING') {
          messages.push({
            id: task.id + 100000,
            projectId,
            role: 'agent',
            author: nameOf(task.agentId),
            text: '',
            status: 'pending',
            rootExecutionId: rootId,
            attachments: [],
          });
        }
        return;
      }
      /*
       * 부모를 따라 올라가 트리의 **진짜 루트**를 찾는다.
       * `latestExecutionId` 가 자식일 수 있는데, 그때 자식 하나의 상태만 보면 **아직 돌고 있는 트리를
       * "끝났다"로 오판**한다(2026-10-08 실측: 실행 162 가 WAITING_CHILD 인데 채팅은 종료로 표시됐다).
       * 판정은 트리 안 **모든 실행**을 보고, 하나라도 안 끝났으면 진행 중으로 둔다.
       */
      const byId = new Map(executionList.map((execution) => [execution.id, execution]));
      let treeRoot = candidate;
      while (treeRoot.parentExecutionId !== null) {
        const parent = byId.get(treeRoot.parentExecutionId);
        if (parent === undefined) break;
        treeRoot = parent;
      }
      const belongsToTree = (execution: Execution): boolean => {
        let cursor: Execution | undefined = execution;
        while (cursor !== undefined) {
          if (cursor.id === treeRoot.id) return true;
          cursor = cursor.parentExecutionId === null ? undefined : byId.get(cursor.parentExecutionId);
        }
        return false;
      };
      const members = executionList.filter(belongsToTree);
      const finished = !members.some((execution) => unfinished(execution.status));
      const root = members.find((execution) => execution.id === treeRoot.id) ?? treeRoot;
      messages.push({
        id: task.id + 100000,
        projectId,
        role: 'agent',
        author: nameOf(root.agentId),
        // 끝났으면 **무엇을 했는지**를 정리해 보여 준다(마스터 요약 + 에이전트별 작업 + 결과).
        text: finished ? workLog(executionList, root.id, nameOf) || '실행이 끝났습니다.' : '',
        status: finished ? (root.status === 'FAILED' ? 'error' : 'done') : 'pending',
        rootExecutionId: root.id,
        attachments: [],
      });
    });
    setChats((prev) => [...prev.filter((message) => message.projectId !== projectId), ...messages]);
  }, []);

  /**
   * 한 프로젝트의 태스크 목록(+필요하면 실행 트리)을 다시 읽는다.
   *
   * <p>실행 트리는 **필요할 때만** 읽는다 — 트리 창을 열어 둔 트리, 아직 모르는 트리,
   * 그리고 실행이 바뀌었다는 알림을 받은(`forceTrees`) **아직 끝나지 않은** 트리뿐이다.
   * 끝난 트리는 이미 들고 있는 것으로 충분하다(태스크의 `latestExecutionId` + 마지막으로 읽은 트리).
   */
  const loadProjectSlice = useCallback(
    async (projectId: number, forceTrees = false) => {
      const limit = chatPageSizeRef.current;
      const rows = await api.listTasks(projectId, limit);
      const taskList = rows.map((task) => mapTask(projectId, task));
      rows.forEach((task, index) => taskRootsRef.current.set(taskList[index].id, task.latestExecutionId));
      setTasks((prev) => [...prev.filter((task) => task.projectId !== projectId), ...taskList]);
      // 요청한 만큼 받았으면 더 있을 수 있고, 그보다 적으면 그게 끝이다.
      setChatHasMore((prev) => ({ ...prev, [projectId]: rows.length >= limit }));

      const known = new Map(executionsRef.current.map((execution) => [execution.id, execution]));
      const openTrees = openTreesRef.current.get(projectId) ?? new Set<number>();
      const wanted = new Set<number>(openTrees);
      taskList.forEach((task) => {
        const rootId = taskRootsRef.current.get(task.id) ?? null;
        if (rootId === null) return;
        const knownRoot = known.get(rootId);
        if (knownRoot === undefined || (forceTrees && unfinished(knownRoot.status))) wanted.add(rootId);
      });

      const trees = await Promise.all([...wanted].map((rootId) => fetchTree(rootId, projectId)));
      const executionList = mergeExecutions(executionsRef.current, wanted, trees.flat());
      executionsRef.current = executionList;
      setExecutions(executionList);
      rebuildChats(projectId, taskList, executionList);
    },
    [fetchTree, rebuildChats],
  );

  /** 파일 목록은 **요청이 있을 때만** 읽는다(첨부 창이 열릴 때). */
  const fetchWorkspaceFiles = useCallback(async (workspaceId: number) => {
    try {
      const paths = await api.listWorkspaceFiles(workspaceId);
      setWorkspaceFiles((prev) => ({ ...prev, [workspaceId]: paths }));
    } catch {
      // 못 읽으면 화면은 빈 목록으로 둔다(첨부 창이 안내 문구를 보여 준다).
    }
  }, []);

  /** 첨부가 바뀌면 **파일 목록을 요청한 적 있는 워크스페이스만** 다시 읽는다(첨부 창이 열려 있지 않으면 아무 일도 없다). */
  const refreshOpenFileLists = useCallback(async () => {
    await Promise.all(Object.keys(filesRef.current).map((key) => fetchWorkspaceFiles(Number(key))));
  }, [fetchWorkspaceFiles]);

  /**
   * 알림 하나를 "다시 읽을 조각"으로 옮긴다. 프로젝트를 알 수 없는 알림은 그 조각 전체(`reload`)를 다시 읽는다.
   * 바뀌지 않은 조각은 건드리지 않는다 — 3초마다 20건을 부르던 방식을 대신한다.
   */
  const applyEvent = useCallback(
    async (event: DataChanged): Promise<void> => {
      switch (event.type) {
        case 'execution.changed':
          if (event.projectId === null) await reloadRef.current();
          else await loadProjectSlice(event.projectId, true);
          return;
        case 'task.changed':
          if (event.projectId === null) await reloadRef.current();
          else await loadProjectSlice(event.projectId);
          return;
        case 'agent.changed':
          if (event.projectId === null) await reloadRef.current();
          else {
            await loadAgents();
            await loadGroups(event.projectId);
          }
          return;
        case 'group.changed':
          if (event.projectId === null) await reloadRef.current();
          else {
            await loadGroups(event.projectId);
            await loadAgents();
          }
          return;
        case 'project.changed':
          await loadProjects();
          return;
        case 'workspace.changed':
          await loadWorkspaces();
          return;
        case 'runtime.changed':
          await loadProviders();
          return;
        case 'attachment.changed':
          await refreshOpenFileLists();
          return;
        default:
          return;
      }
    },
    [
      loadAgents,
      loadGroups,
      loadProjectSlice,
      loadProjects,
      loadProviders,
      loadWorkspaces,
      refreshOpenFileLists,
    ],
  );

  /**
   * 서버 상태를 통째로 다시 읽는다(첫 로드·화면 조작 뒤·안전망 폴링).
   * 조각 로더를 그대로 조립하고, 실행 트리도 같은 규칙(아직 모르는 것만)으로 읽는다.
   */
  const reload = useCallback(async () => {
    try {
      const [projectList] = await Promise.all([
        loadProjects(),
        loadProviders(),
        loadWorkspaces(),
        loadAgents(),
        loadCatalog(),
      ]);

      await Promise.all(
        projectList.map(async (project) => {
          await loadGroups(project.id);
          await loadProjectSlice(project.id);
        }),
      );

      setLoading(false);
      setError(null);
    } catch (ex) {
      setLoading(false);
      fail(ex);
    }
  }, [fail, loadAgents, loadCatalog, loadGroups, loadProjectSlice, loadProjects, loadProviders, loadWorkspaces]);

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(() => {
    reloadRef.current = reload;
  }, [reload]);

  useEffect(() => {
    applyEventRef.current = applyEvent;
  }, [applyEvent]);

  /**
   * 전역 이벤트 스트림(`GET /events/stream`)을 **한 번** 구독한다.
   * 데이터 이벤트는 이름 없이 오므로 `onmessage` 하나로 모두 받고, 250ms 모았다가 바뀐 조각만 다시 읽는다.
   * 끊기면 EventSource 가 스스로 다시 붙고, 그동안은 폴링 안전망이 빨라진다(`streamConnected`).
   */
  useEffect(() => {
    const source = new EventSource(`${api.base}/events/stream`);

    const flush = () => {
      flushRef.current = null;
      const events = [...pendingRef.current.values()];
      pendingRef.current.clear();
      // 한 번에 하나씩 순서대로 다시 읽는다(같은 조각은 이미 하나로 합쳐져 있다).
      void events.reduce((chain, event) => chain.then(() => applyEventRef.current(event)), Promise.resolve());
    };

    const queue = (event: DataChanged) => {
      // 같은 조각(타입 + 프로젝트)은 한 번만 읽는다.
      pendingRef.current.set(`${event.type}:${event.projectId ?? ''}`, event);
      if (flushRef.current !== null) window.clearTimeout(flushRef.current);
      flushRef.current = window.setTimeout(flush, EVENT_DEBOUNCE_MS);
    };

    // 붙었다는 인사(`hello`)와 연결 열림을 연결 상태로 쓴다.
    source.addEventListener('hello', () => setStreamConnected(true));
    source.onopen = () => setStreamConnected(true);
    source.onerror = () => setStreamConnected(false);
    source.onmessage = (message) => {
      try {
        const event = JSON.parse(message.data as string) as DataChanged;
        if (typeof event.type === 'string') queue(event);
      } catch {
        // 알림을 못 읽으면 무시한다(안전망 폴링이 따라잡는다).
      }
    };

    return () => {
      if (flushRef.current !== null) window.clearTimeout(flushRef.current);
      flushRef.current = null;
      pendingRef.current.clear();
      source.close();
    };
  }, []);

  /**
   * 폴링은 **안전망**이다 — 평소에는 이벤트가 화면을 갱신하고, 이 주기는 놓친 것만 줍는다(30초).
   * 스트림이 끊겨 있으면 그동안만 5초로 빠르게 돌고, 탭이 숨겨져 있으면 아예 돌지 않는다(다시 보이면 한 번 읽는다).
   */
  useEffect(() => {
    let timer: number | undefined;
    const period = streamConnected ? SAFETY_POLL_MS : DISCONNECTED_POLL_MS;

    const stop = () => {
      if (timer === undefined) return;
      window.clearInterval(timer);
      timer = undefined;
    };

    const start = () => {
      stop();
      if (document.hidden) return;
      timer = window.setInterval(() => void reloadRef.current(), period);
    };

    const onVisibility = () => {
      if (document.hidden) {
        stop();
        return;
      }
      void reloadRef.current();
      start();
    };

    start();
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      stop();
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [streamConnected]);

  const sendCommand = useCallback(
    (projectId: number, target: ChatTarget, input: string, attachments: AttachedFile[]) => {
      const pushSystem = (message: string) =>
        setChats((prev) => [
          ...prev,
          {
            id: nextId(),
            projectId,
            role: 'system',
            author: '시스템',
            text: message,
            status: 'error',
            rootExecutionId: null,
            attachments: [],
          },
        ]);

      const group = target.kind === 'group' ? groups.find((candidate) => candidate.id === target.id) : undefined;
      if (target.kind === 'group' && group === undefined) {
        pushSystem('팀을 찾을 수 없습니다.');
        return;
      }
      if (group !== undefined && group.leaderAgentId === null) {
        pushSystem(`${group.name} 에는 리더가 없어 명령을 보낼 수 없습니다.`);
        return;
      }
      const receiverId = target.kind === 'group' ? (group?.leaderAgentId ?? null) : target.id;
      const receiver = agents.find((agent) => agent.id === receiverId);
      if (receiver === undefined) {
        pushSystem('받는 에이전트를 찾을 수 없습니다.');
        return;
      }
      if (!receiver.available) {
        pushSystem(`${receiver.name} 은(는) 지금 실행할 수 없습니다. (런타임 꺼짐·삭제 또는 폴더 확인 필요)`);
        return;
      }

      const body = input.trim() === '' ? '첨부한 파일을 확인해줘' : input.trim();
      const agentMessageId = nextId();
      const title = body.length > 28 ? `${body.slice(0, 28)}…` : body;

      setChats((prev) => [
        ...prev,
        {
          id: nextId(),
          projectId,
          role: 'user',
          author: '나',
          text: body,
          targetLabel: target.kind === 'group' ? (group?.name ?? '') : receiver.name,
          status: 'done',
          rootExecutionId: null,
          attachments,
        },
        {
          id: agentMessageId,
          projectId,
          role: 'agent',
          author: receiver.name,
          text: '',
          status: 'pending',
          rootExecutionId: null,
          attachments: [],
        },
      ]);

      api
        .sendCommand(projectId, {
          text: body,
          targetAgentId: target.kind === 'agent' ? receiverId : null,
          groupId: target.kind === 'group' ? target.id : null,
          attachmentIds: attachments.map((attachment) => attachment.id),
        })
        .then((result) => {
          setChats((prev) =>
            prev.map((message) =>
              message.id === agentMessageId ? { ...message, rootExecutionId: result.rootExecutionId } : message,
            ),
          );
          taskRootsRef.current.set(result.taskId, result.rootExecutionId);
          setTasks((prev) => [
            ...prev,
            { id: result.taskId, projectId, title, status: 'RUNNING', agentId: receiver.id },
          ]);
          // 이후 갱신은 **전역 이벤트 스트림**이 몰고 간다 — 실행이 바뀔 때마다 `execution.changed` 가 오고,
          // 그 알림이 이 프로젝트의 태스크·실행 트리·채팅을 다시 읽는다(주기 폴링 없음).
        })
        .catch((ex) => {
          fail(ex);
          setChats((prev) =>
            prev.map((message) =>
              message.id === agentMessageId
                ? { ...message, status: 'error', text: `명령을 보내지 못했습니다: ${String(ex)}` }
                : message,
            ),
          );
        });
    },
    [agents, fail, groups, nextId],
  );

  /** 화면 조작을 서버에 반영하고, 성공하면 서버 상태를 다시 읽는다. */
  const call = useCallback(
    (action: () => Promise<unknown>) => {
      action()
        .then(() => reload())
        .catch(fail);
    },
    [fail, reload],
  );

  const saveLayout = useCallback((projectId: number, body: Parameters<typeof api.saveLayout>[1]) => {
    api.saveLayout(projectId, body).catch(fail);
  }, [fail]);

  const store = useMemo<AgentDockStore>(
    () => ({
      loading,
      error,
      streamConnected,
      providers,
      availableProviderKeys: Object.keys(DEFAULT_PROVIDER_NAMES).filter(
        (key) => !providers.some((provider) => provider.key === key),
      ),
      workspaces,
      workspaceFiles,
      projects,
      agents,
      groups,
      tasks,
      executions,
      chats,
      roles,
      permissionProfiles,
      nodePositions,
      groupPositions,
      reload,

      watchExecutionTree: (projectId, rootExecutionId) => {
        const next = new Map(openTreesRef.current);
        if (rootExecutionId === null) next.delete(projectId);
        else next.set(projectId, new Set([rootExecutionId]));
        openTreesRef.current = next;
        // 창을 열면 그 트리를 곧바로 한 번 다시 읽는다(열자마자 최신 상태가 보이게).
        if (rootExecutionId !== null) void loadProjectSlice(projectId, true);
      },

      toggleProvider: (id) => {
        const provider = providers.find((candidate) => candidate.id === id);
        if (provider !== undefined) call(() => api.setProviderEnabled(id, !provider.enabled));
      },
      deleteProvider: (id) => call(() => api.deleteProvider(id)),
      addProvider: (key, name) => call(() => api.createProvider({ key, name })),
      updateCapabilities: (id, capabilities) => call(() => api.updateProviderCapabilities(id, capabilities)),

      createProject: (name) => call(() => api.createProject({ name })),
      renameProject: (id, name) => call(() => api.updateProject(id, { name })),
      deleteProject: (id) => call(() => api.deleteProject(id)),
      setProjectMaster: (projectId, agentId) => {
        const project = projects.find((candidate) => candidate.id === projectId);
        call(() => api.setProjectMaster(projectId, { agentId, masterPrompt: project?.masterPrompt }));
      },
      setMasterPrompt: (projectId, prompt) => {
        const project = projects.find((candidate) => candidate.id === projectId);
        call(() => api.setProjectMaster(projectId, { agentId: project?.masterAgentId ?? null, masterPrompt: prompt }));
      },
      assignWorkspace: (projectId, workspaceId, asDefault) =>
        call(() => api.assignProjectWorkspace(projectId, workspaceId, asDefault)),
      removeWorkspace: (projectId, workspaceId) => call(() => api.removeProjectWorkspace(projectId, workspaceId)),
      registerWorkspace: async (name, path) => {
        try {
          const created = await api.createWorkspace({ name, path });
          await reload();
          return { id: created.id, name: created.name, path: created.path };
        } catch (ex) {
          fail(ex);
          return null;
        }
      },

      createAgent: (input) => call(() => api.createAgent({ ...input })),
      updateAgent: (id, input) => call(() => api.updateAgent(id, { ...input })),
      deleteAgent: (id) => call(() => api.deleteAgent(id)),

      setAgentPlaced: (id, placed) => {
        const agent = agents.find((candidate) => candidate.id === id);
        if (agent === undefined) return;
        setAgents((prev) => prev.map((candidate) => (candidate.id === id ? { ...candidate, placed } : candidate)));
        if (!placed) {
          setGroups((prev) =>
            prev.map((group) =>
              group.memberIds.includes(id)
                ? { ...group, memberIds: group.memberIds.filter((memberId) => memberId !== id) }
                : group,
            ),
          );
          setNodePositions((prev) => {
            const next = { ...prev };
            delete next[id];
            return next;
          });
        }
        saveLayout(agent.projectId, {
          agents: [{ agentId: id, x: nodePositions[id]?.x ?? null, y: nodePositions[id]?.y ?? null, placed }],
          groups: [],
        });
      },
      setAgentPosition: (agentId, x, y) => {
        const agent = agents.find((candidate) => candidate.id === agentId);
        if (agent === undefined) return;
        setNodePositions((prev) => ({ ...prev, [agentId]: { x, y } }));
        saveLayout(agent.projectId, { agents: [{ agentId, x, y, placed: true }], groups: [] });
      },
      setGroupPosition: (groupId, x, y) => {
        const group = groups.find((candidate) => candidate.id === groupId);
        if (group === undefined) return;
        setGroupPositions((prev) => ({ ...prev, [groupId]: { x, y } }));
        saveLayout(group.projectId, { agents: [], groups: [{ groupId, x, y }] });
      },
      clearPositions: () => {
        setNodePositions({});
        setGroupPositions({});
        projects.forEach((project) => api.clearLayout(project.id).catch(fail));
      },

      createGroup: (projectId, name) => call(() => api.createGroup({ projectId, name })),
      deleteGroup: (id) => call(() => api.deleteGroup(id)),
      addGroupMember: (groupId, agentId) => call(() => api.addGroupMember(groupId, agentId)),
      removeGroupMember: (groupId, agentId) => call(() => api.removeGroupMember(groupId, agentId)),
      setGroupLeader: (groupId, agentId) => {
        const group = groups.find((candidate) => candidate.id === groupId);
        if (group === undefined) return;
        call(() => api.updateGroup(groupId, { name: group.name, prompt: group.prompt, leaderAgentId: agentId }));
      },
      setGroupPrompt: (groupId, prompt) => {
        const group = groups.find((candidate) => candidate.id === groupId);
        if (group === undefined) return;
        setGroups((prev) => prev.map((candidate) => (candidate.id === groupId ? { ...candidate, prompt } : candidate)));
        call(() => api.updateGroup(groupId, { name: group.name, prompt, leaderAgentId: group.leaderAgentId }));
      },
      setGroupNote: (groupId, sharedNote) => {
        const group = groups.find((candidate) => candidate.id === groupId);
        if (group === undefined) return;
        setGroups((prev) =>
          prev.map((candidate) => (candidate.id === groupId ? { ...candidate, sharedNote } : candidate)),
        );
        // 노트만 바꾼다 — 프롬프트와 리더는 지금 값을 그대로 보내 덮어쓰지 않게 한다.
        call(() =>
          api.updateGroup(groupId, {
            name: group.name,
            prompt: group.prompt,
            sharedNote,
            leaderAgentId: group.leaderAgentId,
          }),
        );
      },

      sendCommand,
      removeWorktree: (executionId, deleteBranch) =>
        call(() => api.removeExecutionWorktree(executionId, deleteBranch)),
      chatHasMore,
      loadMoreChats: (projectId) => {
        chatPageSizeRef.current += CHAT_PAGE_SIZE;
        void loadProjectSlice(projectId, true);
      },
      retryMerge: (executionId) => call(() => api.retryExecutionMerge(executionId)),
      answerExecution: (executionId, text) => call(() => api.answerExecution(executionId, text)),
      uploadAttachments: async (workspaceId, files) => {
        try {
          const uploaded: ApiAttachment[] = await api.uploadAttachments(workspaceId, files);
          await fetchWorkspaceFiles(workspaceId);
          return uploaded.map((attachment) => ({
            id: attachment.id,
            workspaceId: attachment.workspaceId,
            path: attachment.storedPath,
            name: attachment.originalName,
          }));
        } catch (ex) {
          fail(ex);
          return [];
        }
      },
      loadWorkspaceFiles: (workspaceId) => void fetchWorkspaceFiles(workspaceId),
    }),
    [
      agents,
      call,
      chats,
      error,
      executions,
      fail,
      fetchWorkspaceFiles,
      groupPositions,
      groups,
      loadProjectSlice,
      loading,
      nodePositions,
      permissionProfiles,
      projects,
      providers,
      reload,
      roles,
      saveLayout,
      sendCommand,
      streamConnected,
      tasks,
      workspaceFiles,
      workspaces,
    ],
  );

  return <StoreContext.Provider value={store}>{children}</StoreContext.Provider>;
}

export function useAgentDockStore(): AgentDockStore {
  const store = useContext(StoreContext);
  if (!store) throw new Error('useAgentDockStore must be used inside AgentDockStoreProvider');
  return store;
}
