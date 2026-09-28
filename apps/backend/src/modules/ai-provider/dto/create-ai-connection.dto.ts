import { Type } from 'class-transformer';
import { IsInt, IsOptional, IsString } from 'class-validator';

export class CreateAiConnectionDto {
  @Type(() => Number)
  @IsInt()
  providerId!: number;

  @IsOptional()
  @IsString()
  accountName?: string;

  /** 실제 Secret이 아니라 OS Credential Store / Secret Store에 대한 참조만 저장한다. */
  @IsOptional()
  @IsString()
  credentialReference?: string;
}
