import { BadRequestException, ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { UserRole } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { AuthenticatedUser } from '../auth/auth.types';
import { CreateMomentDto } from './dto/create-moment.dto';
import { EditBabyDto } from './dto/edit-baby.dto';

@Injectable()
export class BabiesService {
  constructor(private readonly prisma: PrismaService) {}

  async list(user: AuthenticatedUser) {
    const babies = await this.prisma.baby.findMany({
      where: user.role === UserRole.ADMIN
        ? undefined
        : { familyMembers: { some: { userId: user.id } } },
      orderBy: { createdAt: 'asc' },
    });
    return { success: true, data: babies.map(baby => ({ ...baby, canEdit: user.role === UserRole.ADMIN })) };
  }

  async edit(user: AuthenticatedUser, babyId: string, dto: EditBabyDto) {
    if (user.role !== UserRole.ADMIN) throw new ForbiddenException('只有管理员可以编辑宝宝资料');
    await this.requireAccess(user, babyId);
    const birthday = new Date(`${dto.birthday}T00:00:00Z`);
    const today = new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString().slice(0, 10);
    if (!dto.name.trim() || Number.isNaN(birthday.getTime()) || birthday.toISOString().slice(0, 10) !== dto.birthday || dto.birthday > today) {
      throw new BadRequestException('请填写姓名和正确的出生日期，生日不能晚于今天');
    }
    const baby = await this.prisma.baby.update({ where: { id: babyId }, data: {
      name: dto.name.trim(), nickname: dto.nickname?.trim() || null,
      birthday, description: dto.description?.trim() || null,
      ...(dto.avatarAssetId ? { avatarAssetId: dto.avatarAssetId } : {}),
    } });
    return { success: true, data: { ...baby, canEdit: true } };
  }

  async moments(user: AuthenticatedUser, babyId: string, cursor?: string, limit = 20) {
    await this.requireAccess(user, babyId);
    const take = Math.min(Math.max(limit, 1), 50);
    const rows = await this.prisma.moment.findMany({
      where: { babyId, ...(user.role === 'ADMIN' ? {} : {
        OR: [{ visibility: 'FAMILY' }, { authorId: user.id },
          ...(user.role === 'PARENT' ? [{ visibility: 'PARENTS_ONLY' as const }] : [])],
      }) },
      orderBy: [{ eventDate: 'desc' }, { id: 'desc' }],
      cursor: cursor ? { id: cursor } : undefined,
      skip: cursor ? 1 : 0,
      take: take + 1,
      include: {
        author: { select: { id: true, nickname: true, avatar: true } },
        assets: { orderBy: { sortOrder: 'asc' } },
        _count: { select: { comments: true, likes: true } },
        likes: { where: { userId: user.id }, select: { id: true } },
      },
    });
    const hasMore = rows.length > take;
    const members = await this.prisma.familyMember.findMany({where:{babyId}});
    const names = new Map(members.map(m=>[m.userId,m.relationship]));
    const items = rows.slice(0, take).map(({ likes, ...moment }) => ({
      ...moment,
      author: { ...moment.author, nickname: Array.from(new Set([moment.authorId,...moment.contributorIds].map(id=>names.get(id) || (id===moment.authorId ? moment.author.nickname : '家人')))).join('、') },
      likedByMe: likes.length > 0,
    }));
    return {
      success: true,
      data: { items, nextCursor: hasMore ? items.at(-1)?.id ?? null : null },
    };
  }

  async createMoment(user: AuthenticatedUser, babyId: string, dto: CreateMomentDto) {
    if (user.role === UserRole.FAMILY) throw new ForbiddenException('家庭成员不能发布动态');
    await this.requireAccess(user, babyId);
    const uniqueAssets = Array.from(
      new Map(dto.assets.map((asset) => [asset.immichAssetId, asset])).values(),
    ).map((asset, sortOrder) => ({ ...asset, sortOrder }));
    if (!dto.content?.trim() && uniqueAssets.length === 0) {
      throw new BadRequestException('文字和媒体不能同时为空');
    }
    const eventDate = new Date(dto.eventDate);
    const { start, end } = this.albumDayBounds(eventDate);
    const existing = await this.prisma.moment.findFirst({
      where: { babyId, eventDate: { gte: start, lt: end } },
      orderBy: [{ createdAt: 'asc' }, { id: 'asc' }],
      include: { assets: { orderBy: { sortOrder: 'asc' } } },
    });
    if (existing) {
      const existingAssetIds = new Set(existing.assets.map((asset) => asset.immichAssetId));
      const additions = uniqueAssets.filter((asset) => !existingAssetIds.has(asset.immichAssetId));
      const nextSortOrder = existing.assets.reduce((max, asset) => Math.max(max, asset.sortOrder), -1) + 1;
      const originalContent = existing.content?.trim() || '';
      const incomingContent = dto.content?.trim() || '';
      const content = !incomingContent || originalContent === incomingContent || originalContent.endsWith(`\n${incomingContent}`)
        ? originalContent
        : [originalContent, incomingContent].filter(Boolean).join('\n');
      const locations = Array.from(new Set([existing.location?.trim(), dto.location?.trim()].filter(Boolean)));
      const moment = await this.prisma.moment.update({
        where: { id: existing.id },
        data: {
          contributorIds: Array.from(new Set([existing.authorId,...(existing.contributorIds || []),user.id].filter(Boolean))),
          content: content || null,
          location: locations.length ? locations.join('、').slice(0, 255) : null,
          assets: {
            create: additions.map((asset, index) => ({ ...asset, sortOrder: nextSortOrder + index })),
          },
        },
        include: { assets: { orderBy: { sortOrder: 'asc' } } },
      });
      return { success: true, data: moment };
    }
    const moment = await this.prisma.moment.create({
      data: {
        babyId,
        authorId: user.id,
        contributorIds: [user.id],
        content: dto.content?.trim() || null,
        eventDate,
        location: dto.location?.trim() || null,
        visibility: dto.visibility,
        assets: { create: uniqueAssets },
      },
      include: { assets: { orderBy: { sortOrder: 'asc' } } },
    });
    return { success: true, data: moment };
  }

  private albumDayBounds(eventDate: Date) {
    // The family album currently uses China/Singapore local time (UTC+8).
    const offsetMs = 8 * 60 * 60 * 1000;
    const local = new Date(eventDate.getTime() + offsetMs);
    const startMs = Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate()) - offsetMs;
    return { start: new Date(startMs), end: new Date(startMs + 24 * 60 * 60 * 1000) };
  }

  private async requireAccess(user: AuthenticatedUser, babyId: string) {
    const baby = await this.prisma.baby.findUnique({
      where: { id: babyId },
      include: { familyMembers: { where: { userId: user.id }, select: { id: true } } },
    });
    if (!baby) throw new NotFoundException('宝宝不存在');
    if (user.role !== UserRole.ADMIN && baby.familyMembers.length === 0) {
      throw new ForbiddenException('无权查看这个宝宝');
    }
  }
}
