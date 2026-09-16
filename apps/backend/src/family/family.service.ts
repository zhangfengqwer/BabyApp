import { Body, Controller, ForbiddenException, ConflictException, Get, Injectable, Param, ParseUUIDPipe, Patch, Post, UseGuards } from '@nestjs/common';
import { IsString, Matches, MaxLength, MinLength } from 'class-validator';
import { hash } from 'bcryptjs';
import { randomUUID } from 'crypto';
import { PrismaService } from '../prisma/prisma.service';
import { AuthGuard } from '../auth/auth.guard';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/auth.types';
export class FamilyDto {
  @IsString() @Matches(/^[a-zA-Z0-9_.-]{3,64}$/) username: string;
  @IsString() @MinLength(1) @MaxLength(32) relationship: string;
}
export class RelationshipDto { @IsString() @MinLength(1) @MaxLength(32) relationship: string; }
@Injectable()
export class FamilyService {
  constructor(private readonly prisma: PrismaService) {}
  private async access(user: AuthenticatedUser, babyId: string, admin = false) {
    if (admin && user.role !== 'ADMIN') throw new ForbiddenException('只有管理员可管理家庭成员');
    if (user.role !== 'ADMIN' && !await this.prisma.familyMember.findUnique({where:{babyId_userId:{babyId,userId:user.id}}})) throw new ForbiddenException('无权查看家庭成员');
  }
  async list(user: AuthenticatedUser, babyId: string) {
    await this.access(user,babyId);
    const users = await this.prisma.user.findMany({where:{OR:[{role:'ADMIN'},{familyMembers:{some:{babyId}}}]},orderBy:{createdAt:'asc'},include:{familyMembers:{where:{babyId}}}});
    return {success:true,data:users.map(u=>({id:u.id,username:u.username,nickname:u.nickname,role:u.role,relationship:u.familyMembers[0]?.relationship || (u.role==='ADMIN'?'家庭管理员':u.nickname)}))};
  }
  async create(user: AuthenticatedUser, babyId: string, dto: FamilyDto) {
    await this.access(user,babyId,true);
    const username=dto.username.trim().toLowerCase(), relationship=dto.relationship.trim();
    if (!relationship) throw new ConflictException('家庭关系不能为空');
    if (await this.prisma.user.findUnique({where:{username}})) throw new ConflictException('用户名已存在，请使用其他用户名');
    const data=await this.prisma.user.create({data:{username,nickname:relationship,role:'PARENT',passwordHash:await hash(randomUUID(),12),familyMembers:{create:{babyId,relationship}}},select:{id:true,username:true,nickname:true,role:true}});
    return {success:true,data:{...data,relationship}};
  }
  async edit(user: AuthenticatedUser,babyId:string,userId:string, dto:RelationshipDto) {
    await this.access(user,babyId,true);
    const relationship=dto.relationship.trim();
    if(!relationship) throw new ConflictException('家庭关系不能为空');
    const member=await this.prisma.user.findUnique({where:{id:userId},include:{familyMembers:{where:{babyId}}}});
    if(!member || (member.role!=='ADMIN' && !member.familyMembers.length)) throw new ForbiddenException('成员不属于这个家庭');
    const data=await this.prisma.familyMember.upsert({where:{babyId_userId:{babyId,userId}},create:{babyId,userId,relationship},update:{relationship}});
    return {success:true,data};
  }
}
@Controller('babies/:babyId/family') @UseGuards(AuthGuard)
export class FamilyController {
  constructor(private readonly service:FamilyService){}
  @Get() list(@CurrentUser() u:AuthenticatedUser,@Param('babyId',ParseUUIDPipe) b:string){return this.service.list(u,b);}
  @Post() create(@CurrentUser() u:AuthenticatedUser,@Param('babyId',ParseUUIDPipe) b:string,@Body() d:FamilyDto){return this.service.create(u,b,d);}
  @Patch(':userId') edit(@CurrentUser() u:AuthenticatedUser,@Param('babyId',ParseUUIDPipe) b:string,@Param('userId',ParseUUIDPipe) id:string,@Body() d:RelationshipDto){return this.service.edit(u,b,id,d);}
}
