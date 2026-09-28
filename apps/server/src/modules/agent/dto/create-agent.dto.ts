import { IsObject, IsOptional, IsString, MinLength } from 'class-validator';

export class CreateAgentDto {
  @IsString()
  @MinLength(1)
  name!: string;

  @IsString()
  roleId!: string;

  @IsString()
  permissionProfileId!: string;

  @IsString()
  providerId!: string;

  @IsOptional()
  @IsString()
  connectionId?: string;

  @IsOptional()
  @IsString()
  workspaceId?: string;

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
