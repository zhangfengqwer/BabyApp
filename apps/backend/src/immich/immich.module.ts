import { HttpModule } from '@nestjs/axios';
import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { ImmichController } from './immich.controller';
import { ImmichService } from './immich.service';

@Module({
  imports: [HttpModule, AuthModule],
  controllers: [ImmichController],
  providers: [ImmichService],
})
export class ImmichModule {}

