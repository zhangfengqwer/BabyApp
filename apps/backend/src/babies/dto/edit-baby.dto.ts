import { IsOptional, IsString, IsUUID, Matches, MaxLength, MinLength } from 'class-validator';

export class EditBabyDto {
  @IsString() @MinLength(1) @MaxLength(64)
  name: string;

  @IsOptional() @IsString() @MaxLength(64)
  nickname?: string;

  @IsString() @Matches(/^\d{4}-\d{2}-\d{2}$/)
  birthday: string;

  @IsOptional() @IsString() @MaxLength(2000)
  description?: string;

  @IsOptional() @IsUUID()
  avatarAssetId?: string;
}
