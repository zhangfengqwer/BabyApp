import { ForbiddenException, BadRequestException } from '@nestjs/common';
import { MomentsService } from './moments.service';
import { AuthenticatedUser } from '../auth/auth.types';

describe('Moment access and mutations', () => {
  const user: AuthenticatedUser = {id:'member',username:'member',nickname:'家人',role:'FAMILY'};
  const record = (visibility = 'FAMILY') => ({
    id:'moment',authorId:'parent',visibility,
    baby:{familyMembers:[{userId:'member'}]},assets:[{id:'link',immichAssetId:'asset',sortOrder:0}],
  });
  function setup(value: unknown) {
    const prisma = {moment:{findUnique:jest.fn().mockResolvedValue(value),delete:jest.fn()},
      $transaction:jest.fn()};
    return {prisma,service:new MomentsService(prisma as never)};
  }
  it('denies private records to another family member', async()=>{
    const {service}=setup(record('PRIVATE'));
    await expect(service.accessible(user,'moment')).rejects.toBeInstanceOf(ForbiddenException);
  });
  it('denies deleting a record to FAMILY even with membership',async()=>{
    const {service,prisma}=setup(record());
    await expect(service.remove(user,'moment')).rejects.toBeInstanceOf(ForbiddenException);
    expect(prisma.moment.delete).not.toHaveBeenCalled();
  });
  it('rejects cover assets outside the record before writing',async()=>{
    const {service,prisma}=setup(record());
    await expect(service.edit({...user,role:'ADMIN'},'moment',{coverAssetId:'different'})).rejects.toBeInstanceOf(BadRequestException);
    expect(prisma.$transaction).not.toHaveBeenCalled();
  });
  it('denies individual media removal to FAMILY', async () => {
    const { service } = setup(record());
    await expect(service.removeAsset(user, 'moment', 'link')).rejects.toBeInstanceOf(ForbiddenException);
  });
  it('removes only the specified business media association', async () => {
    const prisma = {
      moment: { findUnique: jest.fn().mockResolvedValue(record()), delete: jest.fn() },
      momentAsset: { deleteMany: jest.fn() },
    };
    await new MomentsService(prisma as never).removeAsset({ ...user, role: 'ADMIN' }, 'moment', 'link');
    expect(prisma.momentAsset.deleteMany).toHaveBeenCalledWith({ where: { id: 'link', momentId: 'moment' } });
    expect(prisma.moment.delete).not.toHaveBeenCalled();
  });
});
