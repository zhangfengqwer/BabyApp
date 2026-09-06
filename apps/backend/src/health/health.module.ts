import { HttpModule } from '@nestjs/axios';
import { Module } from '@nestjs/common';
import { HealthController } from './health.controller';
import { HealthService } from './health.service';

@Module({
  imports: [HttpModule.register({ timeout: 3000 })],
  controllers: [HealthController],
  providers: [HealthService],
})
export class HealthModule {}

