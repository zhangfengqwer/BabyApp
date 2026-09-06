import { Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { compare } from 'bcryptjs';
import { createHash, randomUUID, timingSafeEqual } from 'crypto';
import { sign, verify } from 'jsonwebtoken';
import { PrismaService } from '../prisma/prisma.service';
import { LoginDto } from './dto/login.dto';
import { TokenPayload } from './auth.types';

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly config: ConfigService,
  ) {}

  async login(dto: LoginDto) {
    const user = await this.prisma.user.findUnique({
      where: { username: dto.username.trim() },
    });
    if (!user || !(await compare(dto.password, user.passwordHash))) {
      throw new UnauthorizedException('用户名或密码错误');
    }
    return this.issueTokenPair(user);
  }

  async home(accessKey?: string) {
    const expectedKey = this.requiredSecret('HOME_ACCESS_KEY');
    const received = Buffer.from(accessKey ?? '');
    const expected = Buffer.from(expectedKey);
    if (received.length !== expected.length || !timingSafeEqual(received, expected)) {
      throw new UnauthorizedException('家庭访问认证失败');
    }
    const username = this.config.get<string>('INITIAL_ADMIN_USERNAME')?.trim();
    if (!username) throw new UnauthorizedException('家庭账号未配置');
    const user = await this.prisma.user.findUnique({ where: { username } });
    if (!user) throw new UnauthorizedException('家庭账号不存在');
    return this.issueTokenPair(user);
  }

  async webHome(remoteAddress?: string) {
    if (!this.isPrivateNetworkAddress(remoteAddress)) {
      throw new UnauthorizedException('家庭网页仅允许从家庭网络或 Tailscale 访问');
    }
    const username = this.config.get<string>('INITIAL_ADMIN_USERNAME')?.trim();
    if (!username) throw new UnauthorizedException('家庭账号未配置');
    const user = await this.prisma.user.findUnique({ where: { username } });
    if (!user) throw new UnauthorizedException('家庭账号不存在');
    return this.issueTokenPair(user);
  }

  async refresh(rawRefreshToken: string) {
    const payload = await this.verifyToken(rawRefreshToken, 'refresh');
    const stored = await this.prisma.refreshToken.findUnique({
      where: { tokenHash: this.hashToken(rawRefreshToken) },
      include: { user: true },
    });
    if (
      !stored ||
      stored.userId !== payload.sub ||
      stored.revokedAt ||
      stored.expiresAt <= new Date()
    ) {
      throw new UnauthorizedException('登录状态已失效，请重新登录');
    }
    return this.issueTokenPair(stored.user, stored.id);
  }

  async verifyAccessToken(token: string) {
    return this.verifyToken(token, 'access');
  }

  private async issueTokenPair(user: {
    id: string;
    username: string;
    nickname: string;
    role: TokenPayload['role'];
  }, revokeTokenId?: string) {
    const accessTtl = this.numberConfig('JWT_ACCESS_TTL_SECONDS', 900);
    const refreshTtl = this.numberConfig('JWT_REFRESH_TTL_SECONDS', 2_592_000);
    const base = { sub: user.id, role: user.role };
    const accessToken = sign(
      { ...base, type: 'access' } satisfies TokenPayload,
      this.requiredSecret('JWT_ACCESS_SECRET'),
      { expiresIn: accessTtl, jwtid: randomUUID() },
    );
    const refreshToken = sign(
      { ...base, type: 'refresh' } satisfies TokenPayload,
      this.requiredSecret('JWT_REFRESH_SECRET'),
      { expiresIn: refreshTtl, jwtid: randomUUID() },
    );
    const refreshTokenData = {
      userId: user.id,
      tokenHash: this.hashToken(refreshToken),
      expiresAt: new Date(Date.now() + refreshTtl * 1000),
    };
    if (revokeTokenId) {
      await this.prisma.$transaction([
        this.prisma.refreshToken.update({
          where: { id: revokeTokenId },
          data: { revokedAt: new Date() },
        }),
        this.prisma.refreshToken.create({ data: refreshTokenData }),
      ]);
    } else {
      await this.prisma.refreshToken.create({ data: refreshTokenData });
    }
    return {
      success: true,
      data: {
        accessToken,
        refreshToken,
        expiresIn: accessTtl,
        user: {
          id: user.id,
          username: user.username,
          nickname: user.nickname,
          role: user.role,
        },
      },
    };
  }

  private async verifyToken(token: string, type: TokenPayload['type']) {
    try {
      const payload = verify(
        token,
        this.requiredSecret(
          type === 'access' ? 'JWT_ACCESS_SECRET' : 'JWT_REFRESH_SECRET',
        ),
      ) as TokenPayload;
      if (payload.type !== type) throw new Error('Wrong token type');
      return payload;
    } catch {
      throw new UnauthorizedException('登录状态已失效，请重新登录');
    }
  }

  private requiredSecret(name: string) {
    const value = this.config.get<string>(name);
    if (!value || value.length < 32) throw new Error(`${name} must contain at least 32 characters`);
    return value;
  }

  private numberConfig(name: string, fallback: number) {
    const value = Number(this.config.get<string>(name) ?? fallback);
    return Number.isFinite(value) && value > 0 ? value : fallback;
  }

  private hashToken(token: string) {
    return createHash('sha256').update(token).digest('hex');
  }

  private isPrivateNetworkAddress(raw?: string) {
    const address = (raw ?? '').replace(/^::ffff:/, '');
    if (address === '::1' || address.startsWith('127.')) return true;
    if (address.startsWith('10.') || address.startsWith('192.168.')) return true;
    const parts = address.split('.').map(Number);
    if (parts.length !== 4 || parts.some((part) => !Number.isInteger(part))) return false;
    return (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31)
      || (parts[0] === 100 && parts[1] >= 64 && parts[1] <= 127);
  }
}
