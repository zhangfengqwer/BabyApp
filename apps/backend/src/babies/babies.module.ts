import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { BabiesController } from './babies.controller';
import { BabiesService } from './babies.service';

@Module({
  imports: [AuthModule],
  controllers: [BabiesController],
  providers: [BabiesService],
})
export class BabiesModule {}

