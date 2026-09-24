import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { ImmichModule } from '../immich/immich.module';
import { MomentsController } from './moments.controller';
import { MomentsService } from './moments.service';
@Module({ imports: [AuthModule, ImmichModule], controllers: [MomentsController], providers: [MomentsService] })
export class MomentsModule {}
