import { HealthService } from './health.service';

describe('HealthService', () => {
  it('reports healthy dependencies', async () => {
    const prisma = { $queryRaw: jest.fn().mockResolvedValue([{ '?column?': 1 }]) };
    const http = { get: jest.fn().mockReturnValue({ subscribe: () => undefined }) };
    const config = { get: jest.fn().mockReturnValue(undefined) };
    const service = new HealthService(prisma as never, http as never, config as never);

    const result = await service.check();

    expect(result.data.status).toBe('ok');
    expect(result.data.services.database).toBe('up');
    expect(result.data.services.immich).toBe('not_configured');
  });
});

