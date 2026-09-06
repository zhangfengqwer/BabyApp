import { Body, Controller, Get, Param, Post, Query, UseGuards } from '@nestjs/common';
import { AuthGuard } from '../auth/auth.guard';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/auth.types';
import { BabiesService } from './babies.service';
import { CreateMomentDto } from './dto/create-moment.dto';

@Controller('babies')
@UseGuards(AuthGuard)
export class BabiesController {
  constructor(private readonly babies: BabiesService) {}

  @Get()
  list(@CurrentUser() user: AuthenticatedUser) {
    return this.babies.list(user);
  }

  @Get(':id/moments')
  moments(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id') babyId: string,
    @Query('cursor') cursor?: string,
    @Query('limit') limit?: string,
  ) {
    return this.babies.moments(user, babyId, cursor, Number(limit ?? 20));
  }

  @Post(':id/moments')
  createMoment(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id') babyId: string,
    @Body() dto: CreateMomentDto,
  ) {
    return this.babies.createMoment(user, babyId, dto);
  }
}
