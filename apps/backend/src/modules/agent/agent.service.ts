import { Injectable } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';
import { CreateAgentDto } from './dto/create-agent.dto';

const AGENT_INCLUDE = {
  role: true,
  permissionProfile: true,
  provider: true,
  connection: true,
  workspace: true,
} as const;

@Injectable()
export class AgentService {
  constructor(private readonly prisma: PrismaService) {}

  findAll() {
    return this.prisma.agent.findMany({ include: AGENT_INCLUDE, orderBy: { name: 'asc' } });
  }

  findOne(id: number) {
    return this.prisma.agent.findUniqueOrThrow({ where: { id }, include: AGENT_INCLUDE });
  }

  create(dto: CreateAgentDto) {
    return this.prisma.agent.create({
      data: { ...dto, profile: dto.profile as Prisma.InputJsonValue },
      include: AGENT_INCLUDE,
    });
  }
}
