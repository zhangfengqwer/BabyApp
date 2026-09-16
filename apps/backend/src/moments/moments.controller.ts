import { Body, Controller, Get, Post, Patch, Delete, Param, Query, UseGuards, ParseUUIDPipe } from '@nestjs/common';
import { IsString, MaxLength, MinLength, IsOptional, IsDateString, IsUUID } from 'class-validator';
import { AuthGuard } from '../auth/auth.guard';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/auth.types';
import { MomentsService } from './moments.service';
export class CommentDto {
  @IsString() @MinLength(1) @MaxLength(2000) content: string;
}
export class MomentEditDto {
  @IsOptional() @IsString() @MaxLength(5000) content?: string;
  @IsOptional() @IsString() @MaxLength(255) location?: string;
  @IsOptional() @IsDateString() eventDate?: string;
  @IsOptional() @IsUUID() coverAssetId?: string;
}
@Controller('moments')
@UseGuards(AuthGuard)
export class MomentsController {
  constructor(private readonly service: MomentsService) {}
  @Get(':id') detail(@CurrentUser() u:AuthenticatedUser,@Param('id',ParseUUIDPipe) id:string){return this.service.detail(u,id);}
  @Patch(':id') edit(@CurrentUser() u:AuthenticatedUser,@Param('id',ParseUUIDPipe) id:string,@Body() dto:MomentEditDto){return this.service.edit(u,id,dto);}
  @Delete(':id') remove(@CurrentUser() u:AuthenticatedUser,@Param('id',ParseUUIDPipe) id:string){return this.service.remove(u,id);}
  @Delete(':id/assets/:assetId') removeAsset(@CurrentUser() u:AuthenticatedUser,@Param('id',ParseUUIDPipe) id:string,@Param('assetId',ParseUUIDPipe) assetId:string){return this.service.removeAsset(u,id,assetId);}
  @Get(':id/comments') comments(@CurrentUser() u:AuthenticatedUser,@Param('id',ParseUUIDPipe) id:string,@Query('cursor') cursor?:string){return this.service.comments(u,id,cursor);}
  @Post(':id/comments') comment(@CurrentUser() u:AuthenticatedUser,@Param('id',ParseUUIDPipe) id:string,@Body() dto:CommentDto){return this.service.comment(u,id,dto.content);}
  @Post(':id/like') like(@CurrentUser() u:AuthenticatedUser,@Param('id',ParseUUIDPipe) id:string){return this.service.like(u,id,true);}
  @Delete(':id/like') unlike(@CurrentUser() u:AuthenticatedUser,@Param('id',ParseUUIDPipe) id:string){return this.service.like(u,id,false);}
}
