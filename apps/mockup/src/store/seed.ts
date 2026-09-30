import type { AiProvider, Capabilities, CliStatus } from '../types';

/** 등록 가능한 런타임 종류와 기본 표시 이름. 삭제한 런타임은 다시 등록할 수 있다. */
export const DEFAULT_PROVIDER_NAMES: Record<string, string> = {
  CLAUDE_CODE: 'Claude Code',
  CODEX: 'Codex',
  COMMAND_CODE: 'Command Code',
  GEMINI: 'Gemini',
};

const NPM_PREREQUISITE = ['nvm install lts', 'nvm use lts'];

/** 런타임을 새로 등록할 때 들어가는 초기 capabilities(V5/V9 시드와 같은 값). */
export const DEFAULT_CAPABILITIES: Record<string, Capabilities> = {
  CLAUDE_CODE: {
    models: ['opus', 'sonnet', 'fable'],
    modes: ['acceptEdits', 'auto', 'bypassPermissions', 'manual', 'dontAsk', 'plan'],
    notes: 'models 는 `claude --help` 에 나온 alias 예시이며 CLI 버전에 따라 바뀐다. 화면에서 편집한다.',
    install: ['npm install -g @anthropic-ai/claude-code'],
    installRequire: 'npm',
    installPrerequisite: NPM_PREREQUISITE,
  },
  CODEX: {
    models: [],
    modes: [],
    notes: 'CLI 가 이 머신에 없어 미확인. 화면에서 입력한다.',
    install: ['npm install -g @openai/codex'],
    installRequire: 'npm',
    installPrerequisite: NPM_PREREQUISITE,
  },
  COMMAND_CODE: {
    models: [],
    modes: [],
    notes: 'CLI 가 이 머신에 없어 미확인. 화면에서 입력한다.',
    install: ['npm install -g command-code'],
    installRequire: 'npm',
    installPrerequisite: NPM_PREREQUISITE,
  },
  GEMINI: {
    models: [],
    modes: [],
    notes: 'CLI 가 이 머신에 없어 미확인. 화면에서 입력한다.',
    install: ['npm install -g @google/gemini-cli'],
    installRequire: 'npm',
    installPrerequisite: NPM_PREREQUISITE,
  },
};

/** 현재 개발 DB 와 같은 초기 상태: Claude / Command Code 켜짐, Codex / Gemini 는 삭제된 상태. */
export const SEED_PROVIDERS: AiProvider[] = [
  { id: 12, key: 'CLAUDE_CODE', name: 'Claude Code', enabled: true, capabilities: DEFAULT_CAPABILITIES.CLAUDE_CODE },
  { id: 13, key: 'COMMAND_CODE', name: 'Command Code', enabled: true, capabilities: DEFAULT_CAPABILITIES.COMMAND_CODE },
];

/** CLI 확인 창에 보이는 가짜 결과. Claude 만 설치된 것으로 둔다. */
export const CLI_STATUS_BY_KEY: Record<string, CliStatus> = {
  CLAUDE_CODE: {
    runnable: true,
    resolvedPath: 'C:\\Users\\mock\\.local\\bin\\claude.exe',
    version: '2.1.227 (Claude Code)',
    detail: null,
  },
};

export const CLI_STATUS_UNKNOWN: CliStatus = {
  runnable: false,
  resolvedPath: null,
  version: null,
  detail: 'CLI 정보를 확인할 수 없습니다',
};
