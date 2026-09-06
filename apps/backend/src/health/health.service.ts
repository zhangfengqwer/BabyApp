import { HttpService } from '@nestjs/axios';
import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { firstValueFrom } from 'rxjs';
import { PrismaService } from '../prisma/prisma.service';

type ComponentStatus = 'up' | 'down' | 'not_configured';

@Injectable()
export class HealthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly http: HttpService,
    private readonly config: ConfigService,
  ) {}

  async check() {
    const startedAt = Date.now();
    const [database, immich] = await Promise.all([
      this.checkDatabase(),
      this.checkImmich(),
    ]);
    const status = database === 'up' && immich !== 'down' ? 'ok' : 'degraded';

    return {
      success: true,
      data: {
        status,
        services: { backend: 'up' as ComponentStatus, database, immich },
        latencyMs: Date.now() - startedAt,
        timestamp: new Date().toISOString(),
      },
    };
  }

  private async checkDatabase(): Promise<ComponentStatus> {
    try {
      await this.prisma.$queryRaw`SELECT 1`;
      return 'up';
    } catch {
      return 'down';
    }
  }

  private async checkImmich(): Promise<ComponentStatus> {
    const baseUrl = this.config.get<string>('IMMICH_BASE_URL')?.replace(/\/$/, '');
    if (!baseUrl) return 'not_configured';
    try {
      await firstValueFrom(this.http.get(`${baseUrl}/api/server/ping`));
      return 'up';
    } catch {
      return 'down';
    }
  }
}

