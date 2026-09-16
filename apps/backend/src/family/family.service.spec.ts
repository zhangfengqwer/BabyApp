import { ForbiddenException, ConflictException } from '@nestjs/common';
import { FamilyService } from './family.service';
import { AuthenticatedUser } from '../auth/auth.types';
describe('Family relationships',()=>{
  const user:AuthenticatedUser={id:'dad',username:'dad',nickname:'爸爸',role:'PARENT'};
  it('rejects relationship edits by non-admin members',async()=>{
    await expect(new FamilyService({} as never).edit(user,'baby','mom',{relationship:'妈妈'})).rejects.toBeInstanceOf(ForbiddenException);
  });
  it('rejects duplicate usernames',async()=>{
    const prisma={user:{findUnique:jest.fn().mockResolvedValue({id:'exists'})}};
    await expect(new FamilyService(prisma as never).create({...user,role:'ADMIN'},'baby',{username:'dad',relationship:'爸爸'})).rejects.toBeInstanceOf(ConflictException);
  });
});
