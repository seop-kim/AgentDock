import { Module } from '@nestjs/common';
import { ProcessService } from './process.service';

@Module({
  providers: [ProcessService],
  exports: [ProcessService],
})
export class ProcessModule {}
