import { UnauthorizedException } from '@nestjs/common';
import { hash } from 'bcryptjs';
import { UserRole } from '@prisma/client';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  const user = {
    id: '2f9bc377-082f-45fe-9bd3-9c118c863f82',
    username: 'dad',
    nickname: '爸爸',
    role: UserRole.PARENT,
  };

  async function createService(password = 'correct-password') {
    const prisma = {
      user: {
        findUnique: jest.fn().mockResolvedValue({
          ...user,
          passwordHash: await hash(password, 4),
        }),
      },
      refreshToken: {
        create: jest.fn().mockResolvedValue({}),
        findUnique: jest.fn(),
        update: jest.fn().mockResolvedValue({}),
      },
      $transaction: jest.fn().mockResolvedValue([]),
    };
    const config = {
      get: jest.fn((name: string) => ({
        JWT_ACCESS_SECRET: 'access-secret-at-least-32-characters-long',
        JWT_REFRESH_SECRET: 'refresh-secret-at-least-32-characters-long',
        HOME_ACCESS_KEY: 'home-access-key-at-least-32-characters-long',
        INITIAL_ADMIN_USERNAME: 'dad',
      })[name]),
    };
    return {
      service: new AuthService(prisma as never, config as never),
      prisma,
    };
  }

  it('returns a token pair for valid credentials', async () => {
    const { service, prisma } = await createService();
    const result = await service.login({ username: 'dad', password: 'correct-password' });
    expect(result.success).toBe(true);
    expect(result.data.user.nickname).toBe('爸爸');
    expect(prisma.refreshToken.create).toHaveBeenCalledTimes(1);
  });

  it('rejects an invalid password', async () => {
    const { service } = await createService();
    await expect(service.login({ username: 'dad', password: 'wrong' }))
      .rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('issues tokens for the configured home app key', async () => {
    const { service, prisma } = await createService();
    const result = await service.home('home-access-key-at-least-32-characters-long');
    expect(result.data.user.username).toBe('dad');
    expect(prisma.refreshToken.create).toHaveBeenCalledTimes(1);
  });

  it('rejects an invalid home app key', async () => {
    const { service } = await createService();
    await expect(service.home('wrong-key')).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('rotates refresh tokens atomically', async () => {
    const { service, prisma } = await createService();
    const login = await service.login({ username: 'dad', password: 'correct-password' });
    prisma.refreshToken.findUnique.mockResolvedValue({
      id: 'stored-token',
      userId: user.id,
      expiresAt: new Date(Date.now() + 60_000),
      revokedAt: null,
      user,
    });

    const refreshed = await service.refresh(login.data.refreshToken);

    expect(refreshed.data.refreshToken).not.toBe(login.data.refreshToken);
    expect(prisma.$transaction).toHaveBeenCalledTimes(1);
  });
});
