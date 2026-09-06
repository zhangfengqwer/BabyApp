import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { UpdateController } from './update.controller';

@Module({ imports: [AuthModule], controllers: [UpdateController] })
export class UpdateModule {}
