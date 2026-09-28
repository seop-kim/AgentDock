import { BadRequestException, Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { CreateWorkspaceDto } from './dto/create-workspace.dto';
import { isPathAllowed } from './workspace-allowlist';

@Injectable()
export class WorkspaceService {
  constructor(private readonly prisma: PrismaService) {}

  findAll() {
    return this.prisma.workspace.findMany({ orderBy: { name: 'asc' } });
  }

  create(dto: CreateWorkspaceDto) {
    if (!isPathAllowed(dto.path)) {
      throw new BadRequestException(
        `Workspace path "${dto.path}" is not under an allowed root (WORKSPACE_ALLOWED_ROOTS)`,
      );
    }
    return this.prisma.workspace.create({ data: dto });
  }
}
