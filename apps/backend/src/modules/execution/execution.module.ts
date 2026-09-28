import { Module } from '@nestjs/common';
import { RuntimeModule } from '../../common/runtime/runtime.module';
import { PermissionModule } from '../permission/permission.module';
import { ExecutionController } from './execution.controller';
import { ExecutionService } from './execution.service';

@Module({
  imports: [RuntimeModule, PermissionModule],
  controllers: [ExecutionController],
  providers: [ExecutionService],
  exports: [ExecutionService],
})
export class ExecutionModule {}
