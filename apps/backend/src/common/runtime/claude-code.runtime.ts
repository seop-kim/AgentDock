import { Injectable } from '@nestjs/common';
import { ProcessService } from '../../modules/process/process.service';
import { AgentExecutionRequest, AgentExecutionResult, AgentRuntime } from './agent-runtime.interface';

/**
 * Claude Code CLI를 비대화형(print) 모드로 실행하는 Runtime 구현체.
 * 실행 바이너리/인자는 환경변수로 조정 가능하도록 하여 실제 CLI 스펙 변경에 대응한다.
 */
@Injectable()
export class ClaudeCodeRuntime implements AgentRuntime {
  readonly providerKey = 'CLAUDE_CODE';

  constructor(private readonly processService: ProcessService) {}

  async execute(request: AgentExecutionRequest): Promise<AgentExecutionResult> {
    const command = process.env.CLAUDE_CODE_BIN ?? 'claude';
    const args = ['-p', request.prompt, '--output-format', 'text'];
    if (request.model) {
      args.push('--model', request.model);
    }

    const child = this.processService.spawn(request.executionId, command, args, {
      cwd: request.workspacePath,
    });

    return new Promise<AgentExecutionResult>((resolve, reject) => {
      child.stdout.on('data', (data: Buffer) => request.onLog(data.toString(), 'stdout'));
      child.stderr.on('data', (data: Buffer) => request.onLog(data.toString(), 'stderr'));
      child.once('error', (err) => reject(err));
      child.once('exit', (code) => resolve({ exitCode: code ?? -1 }));
    });
  }

  async cancel(executionId: string): Promise<void> {
    this.processService.cancel(executionId);
  }
}
