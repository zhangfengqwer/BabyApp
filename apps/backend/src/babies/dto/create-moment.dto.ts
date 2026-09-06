import { AssetType, MomentVisibility } from '@prisma/client';
import { Type } from 'class-transformer';
import { IsArray, IsDateString, IsEnum, IsInt, IsOptional, IsString, IsUUID, MaxLength, Min, ValidateNested } from 'class-validator';

export class CreateMomentAssetDto {
  @IsUUID()
  immichAssetId: string;

  @IsEnum(AssetType)
  assetType: AssetType;

  @IsInt()
  @Min(0)
  sortOrder: number;
}

export class CreateMomentDto {
  @IsOptional()
  @IsString()
  @MaxLength(5000)
  content?: string;

  @IsDateString()
  eventDate: string;

  @IsOptional()
  @IsString()
  @MaxLength(255)
  location?: string;

  @IsOptional()
  @IsEnum(MomentVisibility)
  visibility?: MomentVisibility;

  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => CreateMomentAssetDto)
  assets: CreateMomentAssetDto[];
}
