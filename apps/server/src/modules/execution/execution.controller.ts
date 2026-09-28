import { Body, Controller, Get, MessageEvent, Param, Post, Sse } from '@nestjs/common';
import { Observable, map } from 'rxjs';
import { ExecutionService } from './execution.service';
import { CreateExecutionDto } from './dto/create-execution.dto';

@Controller('executions')
export class ExecutionController {
  constructor(private readonly executionService: ExecutionService) {}

  @Post()
  create(@Body() dto: CreateExecutionDto) {
    return this.executionService.create(dto.agentId, dto.prompt);
  }

  @Get(':id')
  findOne(@Param('id') id: string) {
    return this.executionService.findOne(id);
  }

  @Get(':id/logs')
  getLogs(@Param('id') id: string) {
    return this.executionService.getLogs(id);
  }

  @Sse(':id/stream')
  stream(@Param('id') id: string): Observable<MessageEvent> {
    return this.executionService.streamLogs(id).pipe(map((log) => ({ data: log })));
  }

  @Post(':id/cancel')
  cancel(@Param('id') id: string) {
    return this.executionService.cancel(id);
  }
}
