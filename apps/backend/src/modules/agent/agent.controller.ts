import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { AgentService } from './agent.service';
import { CreateAgentDto } from './dto/create-agent.dto';

@Controller('agents')
export class AgentController {
  constructor(private readonly agentService: AgentService) {}

  @Get()
  findAll() {
    return this.agentService.findAll();
  }

  @Get(':id')
  findOne(@Param('id') id: string) {
    return this.agentService.findOne(id);
  }

  @Post()
  create(@Body() dto: CreateAgentDto) {
    return this.agentService.create(dto);
  }
}
