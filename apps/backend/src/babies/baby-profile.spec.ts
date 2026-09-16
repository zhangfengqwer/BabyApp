import { BadRequestException, ForbiddenException } from '@nestjs/common';
import { UserRole } from '@prisma/client';
import { BabiesService } from './babies.service';

describe('Baby profile editing', () => {
  const user = { id: 'admin', username: 'admin', nickname: '管理员', role: UserRole.ADMIN };
  const dto = { name: '之之', nickname: '宝贝', birthday: '2022-09-01', description: '每一天都值得珍藏' };
  it('requires administrator permission', async () => {
    await expect(new BabiesService({} as never).edit({ ...user, role: UserRole.FAMILY }, 'baby', dto)).rejects.toBeInstanceOf(ForbiddenException);
  });
  it('updates the profile without changing the existing avatar', async () => {
    const prisma = { baby: { findUnique: jest.fn().mockResolvedValue({ id: 'baby', familyMembers: [] }), update: jest.fn().mockResolvedValue({ id: 'baby', ...dto }) } };
    const result = await new BabiesService(prisma as never).edit(user, 'baby', dto);
    expect(result.data.canEdit).toBe(true);
    expect(prisma.baby.update.mock.calls[0][0].data).not.toHaveProperty('avatarAssetId');
  });
  it('rejects invalid calendar dates', async () => {
    const prisma = { baby: { findUnique: jest.fn().mockResolvedValue({ id: 'baby', familyMembers: [] }) } };
    await expect(new BabiesService(prisma as never).edit(user, 'baby', { ...dto, birthday: '2022-02-31' })).rejects.toBeInstanceOf(BadRequestException);
  });
});
