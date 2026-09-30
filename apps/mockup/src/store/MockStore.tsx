import { createContext, ReactNode, useContext, useMemo, useReducer } from 'react';
import type { AiProvider, Capabilities, Project, Workspace } from '../types';
import {
  DEFAULT_CAPABILITIES,
  DEFAULT_PROVIDER_NAMES,
  SEED_PROJECTS,
  SEED_PROVIDERS,
  SEED_WORKSPACES,
} from './seed';

/** 목업의 메모리 상태. 백엔드/저장소 없이 새로고침하면 시드 값으로 돌아간다. */
interface MockState {
  providers: AiProvider[];
  workspaces: Workspace[];
  projects: Project[];
  nextId: number;
}

type Action =
  | { type: 'provider/toggle'; id: number }
  | { type: 'provider/delete'; id: number }
  | { type: 'provider/add'; key: string; name: string }
  | { type: 'provider/capabilities'; id: number; capabilities: Capabilities }
  | { type: 'project/create'; name: string }
  | { type: 'project/assignWorkspace'; projectId: number; workspaceId: number; asDefault: boolean }
  | { type: 'project/removeWorkspace'; projectId: number; workspaceId: number };

const initialState: MockState = {
  providers: SEED_PROVIDERS,
  workspaces: SEED_WORKSPACES,
  projects: SEED_PROJECTS,
  nextId: 100,
};

const mapProject = (state: MockState, projectId: number, change: (project: Project) => Project): MockState => ({
  ...state,
  projects: state.projects.map((p) => (p.id === projectId ? change(p) : p)),
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
  createProject: (name: string) => void;
  assignWorkspace: (projectId: number, workspaceId: number, asDefault: boolean) => void;
  removeWorkspace: (projectId: number, workspaceId: number) => void;
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
      createProject: (name) => dispatch({ type: 'project/create', name }),
      assignWorkspace: (projectId, workspaceId, asDefault) =>
        dispatch({ type: 'project/assignWorkspace', projectId, workspaceId, asDefault }),
      removeWorkspace: (projectId, workspaceId) =>
        dispatch({ type: 'project/removeWorkspace', projectId, workspaceId }),
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
