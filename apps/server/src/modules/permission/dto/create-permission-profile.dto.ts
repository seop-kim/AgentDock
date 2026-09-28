import { IsBoolean, IsOptional, IsString, MinLength } from 'class-validator';

export class CreatePermissionProfileDto {
  @IsString()
  @MinLength(1)
  name!: string;

  @IsOptional() @IsBoolean() fileRead?: boolean;
  @IsOptional() @IsBoolean() fileWrite?: boolean;
  @IsOptional() @IsBoolean() terminalExecute?: boolean;
  @IsOptional() @IsBoolean() gitStatus?: boolean;
  @IsOptional() @IsBoolean() gitDiff?: boolean;
  @IsOptional() @IsBoolean() gitCommit?: boolean;
  @IsOptional() @IsBoolean() gitPush?: boolean;
  @IsOptional() @IsBoolean() dbRead?: boolean;
  @IsOptional() @IsBoolean() dbWrite?: boolean;
  @IsOptional() @IsBoolean() dbSchemaChange?: boolean;
  @IsOptional() @IsBoolean() deploy?: boolean;
  @IsOptional() @IsBoolean() externalNetworkAccess?: boolean;
}
