import { Injectable, Logger } from '@nestjs/common';
import { ChildProcessWithoutNullStreams, spawn } from 'child_process';

export interface SpawnOptions {
  cwd: string;
  env?: Record<string, string>;
}

/**
 * 로컬 CLI(Claude Code, Codex 등) 프로세스 실행을 담당한다.
 * shell:false로 spawn하여 사용자 Prompt가 Shell Command로 해석되지 않도록 한다.
 */
@Injectable()
export class ProcessService {
  private readonly logger = new Logger(ProcessService.name);
  private readonly processes = new Map<string, ChildProcessWithoutNullStreams>();

  spawn(executionId: string, command: string, args: string[], options: SpawnOptions): ChildProcessWithoutNullStreams {
    const child = spawn(command, args, {
      cwd: options.cwd,
      env: { ...process.env, ...options.env },
      shell: false,
    });
    this.processes.set(executionId, child);
    child.once('exit', () => {
      this.processes.delete(executionId);
    });
    this.logger.log(`spawned execution=${executionId} command=${command}`);
    return child;
  }

  cancel(executionId: string): boolean {
    const child = this.processes.get(executionId);
    if (!child) {
      return false;
    }
    child.kill('SIGTERM');
    return true;
  }

  isRunning(executionId: string): boolean {
    return this.processes.has(executionId);
  }
}
