import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { CreateAgentRoleDto } from './dto/create-agent-role.dto';

@Injectable()
export class RoleService {
  constructor(private readonly prisma: PrismaService) {}

  findAll() {
    return this.prisma.agentRole.findMany({ orderBy: { name: 'asc' } });
  }

  create(dto: CreateAgentRoleDto) {
    return this.prisma.agentRole.create({ data: dto });
  }
}
