import { Type } from 'class-transformer';
import { IsInt, IsObject, IsOptional, IsString, MinLength } from 'class-validator';

export class CreateAgentDto {
  @IsString()
  @MinLength(1)
  name!: string;

  @Type(() => Number)
  @IsInt()
  roleId!: number;

  @Type(() => Number)
  @IsInt()
  permissionProfileId!: number;

  @Type(() => Number)
  @IsInt()
  providerId!: number;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  connectionId?: number;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  workspaceId?: number;

  @IsOptional()
  @IsString()
  model?: string;

  @IsOptional()
  @IsString()
  mode?: string;

  /** Identity/Responsibilities/WorkingRules/CommunicationStyle/SpecialKnowledge 구조화 프로필 */
  @IsOptional()
  @IsObject()
  profile?: Record<string, unknown>;
}
