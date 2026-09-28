import { Injectable } from '@nestjs/common';
import { PermissionProfile } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';
import { CreatePermissionProfileDto } from './dto/create-permission-profile.dto';
import { PermissionAction } from './permission-action.enum';

@Injectable()
export class PermissionService {
  constructor(private readonly prisma: PrismaService) {}

  findAll() {
    return this.prisma.permissionProfile.findMany({ orderBy: { name: 'asc' } });
  }

  create(dto: CreatePermissionProfileDto) {
    return this.prisma.permissionProfile.create({ data: dto });
  }

  /** Backend Tool Layer enforcement: Prompt 설명이 아니라 여기서 실제로 허용 여부를 결정한다. */
  isAllowed(profile: PermissionProfile, action: PermissionAction): boolean {
    return Boolean(profile[action]);
  }
}
