export interface AgentExecutionRequest {
  executionId: string;
  prompt: string;
  workspacePath: string;
  model?: string | null;
  mode?: string | null;
  onLog: (chunk: string, stream: 'stdout' | 'stderr') => void;
}

export interface AgentExecutionResult {
  exitCode: number;
}

/**
 * Agent와 실제 AI 실행 엔진(Claude Code, Codex 등)을 분리하는 경계.
 * 새 Runtime을 추가할 때는 이 인터페이스만 구현하고 RuntimeRegistry에 등록한다.
 */
export interface AgentRuntime {
  readonly providerKey: string;
  execute(request: AgentExecutionRequest): Promise<AgentExecutionResult>;
  cancel(executionId: string): Promise<void>;
}
