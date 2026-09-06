import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { HealthModule } from './health/health.module';
import { PrismaModule } from './prisma/prisma.module';
import { AuthModule } from './auth/auth.module';
import { BabiesModule } from './babies/babies.module';
import { ImmichModule } from './immich/immich.module';
import { MomentsModule } from './moments/moments.module';
import { UpdateModule } from './update/update.module';
import { WebModule } from './web/web.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    PrismaModule,
    HealthModule,
    AuthModule,
    BabiesModule,
    ImmichModule,
    MomentsModule,
    UpdateModule,
    WebModule,
  ],
})
export class AppModule {}
