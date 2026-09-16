import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { FamilyController, FamilyService } from './family.service';
@Module({ imports: [AuthModule], controllers: [FamilyController], providers: [FamilyService] })
export class FamilyModule {}
