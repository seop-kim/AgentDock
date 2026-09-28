import { Module } from '@nestjs/common';
import { PrismaModule } from './prisma/prisma.module';
import { AiProviderModule } from './modules/ai-provider/ai-provider.module';
import { RoleModule } from './modules/role/role.module';
import { PermissionModule } from './modules/permission/permission.module';
import { WorkspaceModule } from './modules/workspace/workspace.module';
import { AgentModule } from './modules/agent/agent.module';
import { ExecutionModule } from './modules/execution/execution.module';

@Module({
  imports: [
    PrismaModule,
    AiProviderModule,
    RoleModule,
    PermissionModule,
    WorkspaceModule,
    AgentModule,
    ExecutionModule,
  ],
})
export class AppModule {}
