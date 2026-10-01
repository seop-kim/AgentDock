import type { Capabilities } from '../lib/api';

/**
 * 화면이 쓰는 표시용 기본값만 남긴다(데이터는 전부 백엔드에서 온다).
 * 런타임을 새로 등록할 때 고를 수 있는 key 와 이름, 그리고 그 기본 capabilities.
 */
export const DEFAULT_PROVIDER_NAMES: Record<string, string> = {
  CLAUDE_CODE: 'Claude Code',
  CODEX: 'Codex',
  COMMAND_CODE: 'Command Code',
  GEMINI: 'Gemini',
};

/** 런타임을 새로 등록할 때 미리 채워 두는 값(이후 화면에서 편집하고 백엔드에 저장한다). */
export const DEFAULT_CAPABILITIES: Record<string, Capabilities> = {
  CLAUDE_CODE: {
    models: ['opus', 'sonnet', 'fable'],
    modes: ['default', 'acceptEdits', 'plan', 'bypassPermissions'],
    notes: 'claude --help 에 나온 alias 기준',
    install: ['npm install -g @anthropic-ai/claude-code'],
    installRequire: 'npm',
    installPrerequisite: ['nvm install lts', 'nvm use lts'],
  },
  CODEX: {
    models: [],
    modes: [],
    notes: 'CLI 설치 후 --help 로 확인해 채운다',
    install: ['npm install -g @openai/codex'],
    installRequire: 'npm',
    installPrerequisite: ['nvm install lts', 'nvm use lts'],
  },
  COMMAND_CODE: {
    models: [],
    modes: [],
    notes: 'CLI 설치 후 --help 로 확인해 채운다',
    install: ['npm install -g command-code'],
    installRequire: 'npm',
    installPrerequisite: ['nvm install lts', 'nvm use lts'],
  },
  GEMINI: {
    models: [],
    modes: [],
    notes: 'CLI 설치 후 --help 로 확인해 채운다',
    install: ['npm install -g @google/gemini-cli'],
    installRequire: 'npm',
    installPrerequisite: ['nvm install lts', 'nvm use lts'],
  },
};

export const PROVIDER_KEY_LABELS = DEFAULT_PROVIDER_NAMES;
