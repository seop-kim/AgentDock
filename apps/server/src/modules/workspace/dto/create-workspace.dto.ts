import { IsOptional, IsString, MinLength } from 'class-validator';

export class CreateWorkspaceDto {
  @IsString()
  @MinLength(1)
  name!: string;

  @IsString()
  @MinLength(1)
  path!: string;

  @IsOptional()
  @IsString()
  description?: string;
}
