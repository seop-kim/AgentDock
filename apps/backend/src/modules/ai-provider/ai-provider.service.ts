import { Injectable } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';
import { CreateAiProviderDto } from './dto/create-ai-provider.dto';
import { CreateAiConnectionDto } from './dto/create-ai-connection.dto';

@Injectable()
export class AiProviderService {
  constructor(private readonly prisma: PrismaService) {}

  findAll() {
    return this.prisma.aiProvider.findMany({ include: { connections: true }, orderBy: { name: 'asc' } });
  }

  create(dto: CreateAiProviderDto) {
    return this.prisma.aiProvider.create({
      data: { ...dto, capabilities: (dto.capabilities ?? {}) as Prisma.InputJsonValue },
    });
  }

  createConnection(dto: CreateAiConnectionDto) {
    return this.prisma.aiConnection.create({ data: dto });
  }

  listConnections(providerId: number) {
    return this.prisma.aiConnection.findMany({ where: { providerId } });
  }
}
