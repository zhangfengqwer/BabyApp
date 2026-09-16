import { Body, Controller, Get, Param, ParseUUIDPipe, Patch, Post, Query, UseGuards } from '@nestjs/common';
import { EditBabyDto } from './dto/edit-baby.dto';
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

  @Patch(':id')
  edit(@CurrentUser() user: AuthenticatedUser, @Param('id', ParseUUIDPipe) babyId: string, @Body() dto: EditBabyDto) {
    return this.babies.edit(user, babyId, dto);
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
