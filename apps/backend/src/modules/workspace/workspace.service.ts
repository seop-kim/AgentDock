import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { readdirSync, statSync } from 'fs';
import { dirname, join, resolve } from 'path';
import { PrismaService } from '../../prisma/prisma.service';
import { CreateWorkspaceDto } from './dto/create-workspace.dto';
import { FsEntry, isUncPath, listRoots } from './workspace-fs';

export interface WorkspaceBrowseResult {
  path: string | null;
  parentPath: string | null;
  entries: FsEntry[];
}

@Injectable()
export class WorkspaceService {
  constructor(private readonly prisma: PrismaService) {}

  findAll() {
    return this.prisma.workspace.findMany({ orderBy: { name: 'asc' } });
  }

  create(dto: CreateWorkspaceDto) {
    if (isUncPath(dto.path)) {
      throw new BadRequestException('Network path (UNC) is not allowed');
    }
    const resolvedPath = resolve(dto.path);
    let stat;
    try {
      stat = statSync(resolvedPath);
    } catch {
      throw new BadRequestException(`Path "${resolvedPath}" does not exist`);
    }
    if (!stat.isDirectory()) {
      throw new BadRequestException(`Path "${resolvedPath}" is not a directory`);
    }
    return this.prisma.workspace.create({ data: { ...dto, path: resolvedPath } });
  }

  browse(path?: string): WorkspaceBrowseResult {
    if (!path) {
      return { path: null, parentPath: null, entries: listRoots() };
    }
    if (isUncPath(path)) {
      throw new BadRequestException('Network path (UNC) is not allowed');
    }

    const resolvedPath = resolve(path);
    let stat;
    try {
      stat = statSync(resolvedPath);
    } catch {
      throw new NotFoundException(`Path "${resolvedPath}" does not exist`);
    }
    if (!stat.isDirectory()) {
      throw new BadRequestException(`Path "${resolvedPath}" is not a directory`);
    }

    const entries = readdirSync(resolvedPath)
      .flatMap((name) => {
        const entryPath = join(resolvedPath, name);
        try {
          return statSync(entryPath).isDirectory() ? [{ name, path: entryPath }] : [];
        } catch {
          return [];
        }
      })
      .sort((a, b) => a.name.localeCompare(b.name));

    const parent = dirname(resolvedPath);
    const isFsRoot = parent === resolvedPath;
    return { path: resolvedPath, parentPath: isFsRoot ? null : parent, entries };
  }
}
