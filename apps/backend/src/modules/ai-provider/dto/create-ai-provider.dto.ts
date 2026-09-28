import { IsIn, IsObject, IsOptional, IsString, MinLength } from 'class-validator';

const PROVIDER_KEYS = ['CLAUDE_CODE', 'CODEX', 'COMMAND_CODE', 'GEMINI'] as const;

export class CreateAiProviderDto {
  @IsIn(PROVIDER_KEYS)
  key!: (typeof PROVIDER_KEYS)[number];

  @IsString()
  @MinLength(1)
  name!: string;

  /** 예: { modes: ["plan", "execute"], models: ["claude-sonnet-5"] } */
  @IsOptional()
  @IsObject()
  capabilities?: Record<string, unknown>;
}
