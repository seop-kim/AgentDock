import { BadRequestException, ForbiddenException, Injectable, Logger } from '@nestjs/common';
import { Observable, Subject } from 'rxjs';
import { PrismaService } from '../../prisma/prisma.service';
import { RuntimeRegistry } from '../../common/runtime/runtime.registry';
import { PermissionService } from '../permission/permission.service';
import { PermissionAction } from '../permission/permission-action.enum';

interface LogEvent {
  stream: 'stdout' | 'stderr';
  content: string;
}

const AGENT_INCLUDE = {
  permissionProfile: true,
  provider: true,
  workspace: true,
} as const;

@Injectable()
export class ExecutionService {
  private readonly logger = new Logger(ExecutionService.name);
  private readonly streams = new Map<string, Subject<LogEvent>>();

  constructor(
    private readonly prisma: PrismaService,
    private readonly runtimeRegistry: RuntimeRegistry,
    private readonly permissionService: PermissionService,
  ) {}

  async findOne(id: string) {
    return this.prisma.execution.findUniqueOrThrow({ where: { id } });
  }

  async getLogs(executionId: string) {
    return this.prisma.executionLog.findMany({
      where: { executionId },
      orderBy: { createdAt: 'asc' },
    });
  }

  async create(agentId: string, prompt: string) {
    const agent = await this.prisma.agent.findUniqueOrThrow({
      where: { id: agentId },
      include: AGENT_INCLUDE,
    });

    if (!agent.workspace) {
      throw new BadRequestException('Agent has no workspace assigned');
    }

    // Permission Enforcement: Prompt 설명이 아니라 실행 전 Backend에서 실제로 차단한다.
    if (!this.permissionService.isAllowed(agent.permissionProfile, PermissionAction.TERMINAL_EXECUTE)) {
      throw new ForbiddenException('Agent permission profile does not allow TERMINAL_EXECUTE');
    }

    const execution = await this.prisma.execution.create({
      data: { agentId, workspaceId: agent.workspace.id, prompt, status: 'PENDING' },
    });

    this.run(execution.id, agent, prompt).catch((err) => this.logger.error(err));

    return execution;
  }

  streamLogs(executionId: string): Observable<LogEvent> {
    const existing = this.streams.get(executionId);
    if (existing) {
      return existing.asObservable();
    }
    return new Observable<LogEvent>((subscriber) => subscriber.complete());
  }

  async cancel(executionId: string) {
    const execution = await this.prisma.execution.findUniqueOrThrow({
      where: { id: executionId },
      include: { agent: { include: { provider: true } } },
    });
    const runtime = this.runtimeRegistry.resolve(execution.agent.provider.key);
    await runtime.cancel(executionId);
    return { cancelled: true };
  }

  private async run(executionId: string, agent: { provider: { key: string }; workspace: { path: string } | null; model: string | null; mode: string | null }, prompt: string) {
    const subject = new Subject<LogEvent>();
    this.streams.set(executionId, subject);

    await this.prisma.execution.update({
      where: { id: executionId },
      data: { status: 'RUNNING', startedAt: new Date() },
    });

    try {
      const runtime = this.runtimeRegistry.resolve(agent.provider.key);
      const result = await runtime.execute({
        executionId,
        prompt,
        workspacePath: agent.workspace!.path,
        model: agent.model,
        mode: agent.mode,
        onLog: (chunk, stream) => {
          subject.next({ stream, content: chunk });
          this.persistLog(executionId, stream, chunk);
        },
      });

      await this.prisma.execution.update({
        where: { id: executionId },
        data: {
          status: result.exitCode === 0 ? 'SUCCEEDED' : 'FAILED',
          exitCode: result.exitCode,
          finishedAt: new Date(),
        },
      });
    } catch (err) {
      await this.prisma.execution.update({
        where: { id: executionId },
        data: { status: 'FAILED', errorMessage: String(err), finishedAt: new Date() },
      });
    } finally {
      subject.complete();
      this.streams.delete(executionId);
    }
  }

  private persistLog(executionId: string, stream: 'stdout' | 'stderr', content: string) {
    this.prisma.executionLog
      .create({ data: { executionId, stream: stream.toUpperCase() as 'STDOUT' | 'STDERR', content } })
      .catch((err) => this.logger.error(err));
  }
}
