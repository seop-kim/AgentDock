import { Body, Controller, Get, Post, Query } from '@nestjs/common';
import { WorkspaceService } from './workspace.service';
import { CreateWorkspaceDto } from './dto/create-workspace.dto';

@Controller('workspaces')
export class WorkspaceController {
  constructor(private readonly workspaceService: WorkspaceService) {}

  @Get()
  findAll() {
    return this.workspaceService.findAll();
  }

  @Get('browse')
  browse(@Query('path') path?: string) {
    return this.workspaceService.browse(path);
  }

  @Post()
  create(@Body() dto: CreateWorkspaceDto) {
    return this.workspaceService.create(dto);
  }
}
