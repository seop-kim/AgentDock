import { Module } from '@nestjs/common';
import { ProcessModule } from '../../modules/process/process.module';
import { ClaudeCodeRuntime } from './claude-code.runtime';
import { RuntimeRegistry } from './runtime.registry';

@Module({
  imports: [ProcessModule],
  providers: [ClaudeCodeRuntime, RuntimeRegistry],
  exports: [RuntimeRegistry],
})
export class RuntimeModule {}
