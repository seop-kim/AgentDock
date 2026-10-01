import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
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

export type NewAgentInput = Omit<Agent, 'id' | 'placed'>;
export type AgentUpdateInput = Omit<Agent, 'id' | 'projectId' | 'placed'>;

type Position = { x: number; y: number };

interface AgentDockStore {
  loading: boolean;
  error: string | null;

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

  sendCommand: (projectId: number, target: ChatTarget, text: string, attachments: AttachedFile[]) => void;
  uploadAttachments: (workspaceId: number, files: File[]) => Promise<AttachedFile[]>;
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
    case 'CANCELLED':
      return 'FAILED';
    case 'RUNNING':
    case 'WAITING_CHILD':
    case 'FAILED':
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
  };
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

  const seqRef = useRef(1000);
  const pollRef = useRef<number | null>(null);
  const nextId = useCallback(() => {
    seqRef.current += 1;
    return seqRef.current;
  }, []);

  const fail = useCallback((ex: unknown) => setError(String(ex)), []);

  /** 서버 상태를 통째로 다시 읽어 화면 모양으로 바꾼다. */
  const reload = useCallback(async () => {
    try {
      const [providerList, workspaceList, projectList, agentList, roleList, profileList] = await Promise.all([
        api.listProviders(),
        api.listWorkspaces(),
        api.listProjects(),
        api.listAgents(),
        api.listRoles(),
        api.listPermissionProfiles(),
      ]);

      const groupList: AgentGroup[] = [];
      const taskList: Task[] = [];
      const executionList: Execution[] = [];
      const chatList: ChatMessage[] = [];
      const positions: Record<number, Position> = {};
      const boxPositions: Record<number, Position> = {};

      const nameOf = (agentId: number) => agentList.find((agent) => agent.id === agentId)?.name ?? '에이전트';

      for (const project of projectList) {
        const [projectGroups, projectTasks] = await Promise.all([
          api.listGroups(project.id),
          api.listTasks(project.id),
        ]);

        projectGroups.forEach((group: ApiGroup) => {
          groupList.push({
            id: group.id,
            projectId: group.projectId,
            name: group.name,
            leaderAgentId: group.leader?.id ?? null,
            memberIds: group.members.map((member) => member.id),
            prompt: text(group.prompt),
          });
          if (group.nodeX !== null && group.nodeX !== undefined && group.nodeY !== null && group.nodeY !== undefined) {
            boxPositions[group.id] = { x: group.nodeX, y: group.nodeY };
          }
        });

        projectTasks.forEach((task: ApiTask) => {
          taskList.push({
            id: task.id,
            projectId: project.id,
            title: task.title,
            status: taskStatus(task.status),
            agentId: task.agent?.id ?? null,
          });
          chatList.push({
            id: task.id,
            projectId: project.id,
            role: 'user',
            author: '나',
            text: task.title,
            status: 'done',
            rootExecutionId: task.latestExecutionId,
            attachments: [],
          });
        });

        for (const task of projectTasks) {
          if (task.latestExecutionId === null) continue;
          try {
            const tree: ExecutionTree = await api.getExecutionTree(task.latestExecutionId);
            tree.nodes.forEach((node) => executionList.push(executionOf(node.execution, project.id)));
            const root = tree.nodes.find((node) => node.depth === 0)?.execution;
            if (root !== undefined) {
              chatList.push({
                id: task.id + 100000,
                projectId: project.id,
                role: 'agent',
                author: nameOf(root.agentId),
                text: summaryOf(root.resultText) || '실행이 끝났습니다.',
                status: root.status === 'FAILED' ? 'error' : 'done',
                rootExecutionId: root.id,
                attachments: [],
              });
            }
          } catch {
            // 트리를 못 읽는 실행은 건너뛴다.
          }
        }
      }

      agentList.forEach((agent: ApiAgent) => {
        if (agent.nodeX !== null && agent.nodeX !== undefined && agent.nodeY !== null && agent.nodeY !== undefined) {
          positions[agent.id] = { x: agent.nodeX, y: agent.nodeY };
        }
      });

      const files: Record<number, string[]> = {};
      for (const workspace of workspaceList) {
        try {
          files[workspace.id] = await api.listWorkspaceFiles(workspace.id);
        } catch {
          files[workspace.id] = [];
        }
      }

      setProviders(
        providerList.map((provider: ApiProvider) => ({
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
        })),
      );
      setWorkspaces(workspaceList.map((workspace) => ({ id: workspace.id, name: workspace.name, path: workspace.path })));
      setWorkspaceFiles(files);
      setProjects(
        projectList.map((project: ApiProject) => ({
          id: project.id,
          name: project.name,
          workspaces: project.workspaces.map((workspace) => ({
            workspaceId: workspace.workspaceId,
            isDefault: workspace.isDefault,
          })),
          masterAgentId: project.masterAgent?.id ?? null,
          masterPrompt: text(project.masterPrompt),
        })),
      );
      setAgents(
        agentList.map((agent: ApiAgent) => ({
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
        })),
      );
      setGroups(groupList);
      setTasks(taskList);
      setExecutions(executionList);
      setChats(chatList.sort((left, right) => left.id - right.id));
      setRoles(roleList.map((role) => ({ id: role.id, name: role.name })));
      setPermissionProfiles(profileList.map((profile) => ({ id: profile.id, name: profile.name })));
      setNodePositions(positions);
      setGroupPositions(boxPositions);
      setLoading(false);
      setError(null);
    } catch (ex) {
      setLoading(false);
      fail(ex);
    }
  }, [fail]);

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(
    () => () => {
      if (pollRef.current !== null) window.clearTimeout(pollRef.current);
    },
    [],
  );

  /** 실행 트리를 따라가며 화면을 갱신하고, 끝나면 서버 상태를 다시 읽는다. */
  const watch = useCallback(
    (projectId: number, rootExecutionId: number, agentMessageId: number) => {
      const poll = async () => {
        try {
          const tree = await api.getExecutionTree(rootExecutionId);
          const rows = tree.nodes.map((node) => executionOf(node.execution, projectId));
          const root = tree.nodes.find((node) => node.depth === 0)?.execution;
          const finished = root !== undefined && (root.status === 'SUCCEEDED' || root.status === 'FAILED');
          setExecutions((prev) => [...prev.filter((execution) => execution.projectId !== projectId), ...rows]);
          setChats((prev) =>
            prev.map((message) =>
              message.id === agentMessageId
                ? {
                    ...message,
                    status: finished ? (root.status === 'SUCCEEDED' ? 'done' : 'error') : 'pending',
                    text: finished ? summaryOf(root.resultText) || '실행이 끝났습니다.' : '',
                  }
                : message,
            ),
          );
          if (finished) {
            pollRef.current = null;
            await reload();
            return;
          }
        } catch (ex) {
          fail(ex);
        }
        pollRef.current = window.setTimeout(() => void poll(), 1500);
      };
      void poll();
    },
    [fail, reload],
  );

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
          setTasks((prev) => [
            ...prev,
            { id: result.taskId, projectId, title, status: 'RUNNING', agentId: receiver.id },
          ]);
          if (pollRef.current !== null) window.clearTimeout(pollRef.current);
          watch(projectId, result.rootExecutionId, agentMessageId);
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
    [agents, fail, groups, nextId, watch],
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

      sendCommand,
      uploadAttachments: async (workspaceId, files) => {
        try {
          const uploaded: ApiAttachment[] = await api.uploadAttachments(workspaceId, files);
          const paths = await api.listWorkspaceFiles(workspaceId);
          setWorkspaceFiles((prev) => ({ ...prev, [workspaceId]: paths }));
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
      loadWorkspaceFiles: (workspaceId) => {
        api
          .listWorkspaceFiles(workspaceId)
          .then((paths) => setWorkspaceFiles((prev) => ({ ...prev, [workspaceId]: paths })))
          .catch(() => {});
      },
    }),
    [
      agents,
      call,
      chats,
      error,
      executions,
      fail,
      groupPositions,
      groups,
      loading,
      nodePositions,
      permissionProfiles,
      projects,
      providers,
      reload,
      roles,
      saveLayout,
      sendCommand,
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
