import { Controller, Get, Param, Post, Query, Req, Res, UseGuards, ForbiddenException } from '@nestjs/common';
import { UserRole } from '@prisma/client';
import { Request, Response } from 'express';
import { AuthGuard } from '../auth/auth.guard';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/auth.types';
import { ImmichService } from './immich.service';

@Controller('media')
@UseGuards(AuthGuard)
export class ImmichController {
  constructor(private readonly immich: ImmichService) {}

  @Post('assets')
  upload(
    @CurrentUser() user: AuthenticatedUser,
    @Req() request: Request,
    @Res() response: Response,
  ) {
    if (user.role === UserRole.FAMILY) throw new ForbiddenException('家庭成员不能发布媒体');
    return this.immich.upload(request, response);
  }

  @Get('assets/:id/view')
  view(
    @Param('id') assetId: string,
    @Query('size') size: string | undefined,
    @Req() request: Request,
    @Res() response: Response,
  ) {
    const allowed = new Set(['thumbnail', 'preview', 'fullsize']);
    return this.immich.view(assetId, allowed.has(size ?? '') ? size! : 'thumbnail', request, response);
  }
}

