import { BadRequestException, ConflictException, ForbiddenException } from '@nestjs/common';
import { MomentsService } from './moments.service';
import { AuthenticatedUser } from '../auth/auth.types';

describe('Moment access and media deletion', () => {
  const member: AuthenticatedUser = { id: 'member', username: 'member', nickname: '家人', role: 'FAMILY' };
  const admin: AuthenticatedUser = { ...member, role: 'ADMIN' };
  const record = (visibility = 'FAMILY') => ({
    id: 'moment', authorId: 'parent', visibility,
    baby: { familyMembers: [{ userId: 'member' }] },
    assets: [{ id: 'link', immichAssetId: 'asset', sortOrder: 0 }],
  });
  function setup(value: unknown = record()) {
    const prisma = {
      moment: { findUnique: jest.fn().mockResolvedValue(value), delete: jest.fn().mockResolvedValue({}) },
      momentAsset: { findFirst: jest.fn().mockResolvedValue(null), deleteMany: jest.fn().mockResolvedValue({ count: 1 }) },
      baby: { findFirst: jest.fn().mockResolvedValue(null) },
      milestone: { findFirst: jest.fn().mockResolvedValue(null) },
      user: { findFirst: jest.fn().mockResolvedValue(null) },
      $transaction: jest.fn(),
    };
    const immich = { deleteAssets: jest.fn().mockResolvedValue(undefined) };
    return { prisma, immich, service: new MomentsService(prisma as never, immich as never) };
  }

  it('denies private records to another family member', async () => {
    const { service } = setup(record('PRIVATE'));
    await expect(service.accessible(member, 'moment')).rejects.toBeInstanceOf(ForbiddenException);
  });
  it('denies media deletion to a family member', async () => {
    const { service, immich } = setup();
    await expect(service.removeAsset(member, 'moment', 'link')).rejects.toBeInstanceOf(ForbiddenException);
    expect(immich.deleteAssets).not.toHaveBeenCalled();
  });
  it('rejects cover assets outside the record before writing', async () => {
    const { service, prisma } = setup();
    await expect(service.edit(admin, 'moment', { coverAssetId: 'different' })).rejects.toBeInstanceOf(BadRequestException);
    expect(prisma.$transaction).not.toHaveBeenCalled();
  });
  it('rejects a batch containing media from another record before deleting', async () => {
    const { service, immich } = setup();
    await expect(service.removeAssets(admin, 'moment', ['link', 'other'])).rejects.toBeInstanceOf(BadRequestException);
    expect(immich.deleteAssets).not.toHaveBeenCalled();
  });
  it('preserves an original still used by another record or avatar', async () => {
    const { service, prisma, immich } = setup();
    prisma.baby.findFirst.mockResolvedValue({ id: 'baby' });
    await expect(service.removeAssets(admin, 'moment', ['link'])).rejects.toBeInstanceOf(ConflictException);
    expect(immich.deleteAssets).not.toHaveBeenCalled();
    expect(prisma.momentAsset.deleteMany).not.toHaveBeenCalled();
  });
  it('deletes the Immich original before its selected record link', async () => {
    const { service, prisma, immich } = setup();
    await service.removeAssets(admin, 'moment', ['link', 'link']);
    expect(immich.deleteAssets).toHaveBeenCalledWith(['asset']);
    expect(prisma.momentAsset.deleteMany).toHaveBeenCalledWith({ where: { momentId: 'moment', id: { in: ['link'] } } });
    expect(immich.deleteAssets.mock.invocationCallOrder[0]).toBeLessThan(prisma.momentAsset.deleteMany.mock.invocationCallOrder[0]);
  });
  it('retains the record link when Immich deletion fails', async () => {
    const { service, prisma, immich } = setup();
    immich.deleteAssets.mockRejectedValue(new Error('Immich unavailable'));
    await expect(service.removeAssets(admin, 'moment', ['link'])).rejects.toThrow();
    expect(prisma.momentAsset.deleteMany).not.toHaveBeenCalled();
  });
  it('deletes a whole record only after deleting its original media', async () => {
    const { service, prisma, immich } = setup();
    await service.remove(admin, 'moment');
    expect(immich.deleteAssets).toHaveBeenCalledWith(['asset']);
    expect(prisma.moment.delete).toHaveBeenCalledWith({ where: { id: 'moment' } });
    expect(immich.deleteAssets.mock.invocationCallOrder[0]).toBeLessThan(prisma.moment.delete.mock.invocationCallOrder[0]);
  });
});
