import { Body, Controller, Get, Post } from '@nestjs/common';
import { PermissionService } from './permission.service';
import { CreatePermissionProfileDto } from './dto/create-permission-profile.dto';

@Controller('permission-profiles')
export class PermissionController {
  constructor(private readonly permissionService: PermissionService) {}

  @Get()
  findAll() {
    return this.permissionService.findAll();
  }

  @Post()
  create(@Body() dto: CreatePermissionProfileDto) {
    return this.permissionService.create(dto);
  }
}
