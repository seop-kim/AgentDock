import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { AiProviderService } from './ai-provider.service';
import { CreateAiProviderDto } from './dto/create-ai-provider.dto';
import { CreateAiConnectionDto } from './dto/create-ai-connection.dto';

@Controller('ai-providers')
export class AiProviderController {
  constructor(private readonly aiProviderService: AiProviderService) {}

  @Get()
  findAll() {
    return this.aiProviderService.findAll();
  }

  @Post()
  create(@Body() dto: CreateAiProviderDto) {
    return this.aiProviderService.create(dto);
  }

  @Post('connections')
  createConnection(@Body() dto: CreateAiConnectionDto) {
    return this.aiProviderService.createConnection(dto);
  }

  @Get(':id/connections')
  listConnections(@Param('id') id: string) {
    return this.aiProviderService.listConnections(id);
  }
}
