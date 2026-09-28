import { IsString, MinLength } from 'class-validator';

export class CreateExecutionDto {
  @IsString()
  agentId!: string;

  @IsString()
  @MinLength(1)
  prompt!: string;
}
