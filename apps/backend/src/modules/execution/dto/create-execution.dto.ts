import { Type } from 'class-transformer';
import { IsInt, IsString, MinLength } from 'class-validator';

export class CreateExecutionDto {
  @Type(() => Number)
  @IsInt()
  agentId!: number;

  @IsString()
  @MinLength(1)
  prompt!: string;
}
