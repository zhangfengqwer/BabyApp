import { ForbiddenException } from '@nestjs/common';
import { AssetType, UserRole } from '@prisma/client';
import { BabiesService } from './babies.service';

describe('BabiesService permissions', () => {
  const dto = {
    content: '第一张成长照片',
    eventDate: '2026-08-31T10:00:00.000Z',
    assets: [{
      immichAssetId: 'ef96f635-61c7-4639-9e60-61a11c4bbfba',
      assetType: AssetType.IMAGE,
      sortOrder: 0,
    }],
  };

  it('rejects FAMILY publishing', async () => {
    const service = new BabiesService({} as never);
    await expect(service.createMoment(
      { id: 'user', username: 'grandma', nickname: '奶奶', role: UserRole.FAMILY },
      'baby',
      dto,
    )).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('allows PARENT publishing for an accessible baby', async () => {
    const prisma = {
      baby: { findUnique: jest.fn().mockResolvedValue({ id: 'baby', familyMembers: [{ id: 'member' }] }) },
      moment: { findFirst: jest.fn().mockResolvedValue(null), create: jest.fn().mockResolvedValue({ id: 'moment', assets: dto.assets }) },
    };
    const service = new BabiesService(prisma as never);
    const result = await service.createMoment(
      { id: 'user', username: 'dad', nickname: '爸爸', role: UserRole.PARENT },
      'baby',
      dto,
    );
    expect(result.success).toBe(true);
    expect(prisma.moment.create).toHaveBeenCalledTimes(1);
  });

  it('deduplicates Immich assets while preserving first occurrence', async () => {
    const prisma = {
      baby: { findUnique: jest.fn().mockResolvedValue({ id: 'baby', familyMembers: [{ id: 'member' }] }) },
      moment: { findFirst: jest.fn().mockResolvedValue(null), create: jest.fn().mockResolvedValue({ id: 'moment', assets: dto.assets }) },
    };
    const service = new BabiesService(prisma as never);
    await service.createMoment(
      { id: 'user', username: 'dad', nickname: '爸爸', role: UserRole.PARENT },
      'baby',
      { ...dto, assets: [dto.assets[0], { ...dto.assets[0], sortOrder: 1 }] },
    );

    const createCall = prisma.moment.create.mock.calls[0][0];
    expect(createCall.data.assets.create).toEqual([{ ...dto.assets[0], sortOrder: 0 }]);
  });

  it('appends media to the existing moment on the same album day', async () => {
    const existing = { id: 'existing', content: '上午', location: null, assets: [] };
    const prisma = {
      baby: { findUnique: jest.fn().mockResolvedValue({ id: 'baby', familyMembers: [{ id: 'member' }] }) },
      moment: {
        findFirst: jest.fn().mockResolvedValue(existing),
        update: jest.fn().mockResolvedValue({ ...existing, assets: dto.assets }),
      },
    };
    const service = new BabiesService(prisma as never);
    const result = await service.createMoment(
      { id: 'user', username: 'dad', nickname: '爸爸', role: UserRole.PARENT },
      'baby',
      dto,
    );
    expect(result.data.id).toBe('existing');
    expect(prisma.moment.update).toHaveBeenCalledTimes(1);
  });
});
