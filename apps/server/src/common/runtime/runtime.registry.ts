import { Injectable, NotFoundException } from '@nestjs/common';
import { AgentRuntime } from './agent-runtime.interface';
import { ClaudeCodeRuntime } from './claude-code.runtime';

/**
 * provider key -> AgentRuntime 구현체 매핑.
 * 새 Runtime(Codex, Gemini 등)을 추가할 때는 이 등록만 늘리면 되고 나머지 모듈은 건드리지 않는다.
 */
@Injectable()
export class RuntimeRegistry {
  private readonly runtimes = new Map<string, AgentRuntime>();

  constructor(claudeCodeRuntime: ClaudeCodeRuntime) {
    this.register(claudeCodeRuntime);
  }

  register(runtime: AgentRuntime) {
    this.runtimes.set(runtime.providerKey, runtime);
  }

  resolve(providerKey: string): AgentRuntime {
    const runtime = this.runtimes.get(providerKey);
    if (!runtime) {
      throw new NotFoundException(`No AgentRuntime registered for provider "${providerKey}"`);
    }
    return runtime;
  }
}
