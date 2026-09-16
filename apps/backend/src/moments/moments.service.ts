import { ForbiddenException, Injectable, NotFoundException, BadRequestException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { AuthenticatedUser } from '../auth/auth.types';
import { MomentEditDto } from './moments.controller';

@Injectable()
export class MomentsService {
  constructor(private readonly prisma: PrismaService) {}
  async accessible(user: AuthenticatedUser, id: string, edit = false) {
    const moment = await this.prisma.moment.findUnique({
      where: { id }, include: { baby: { include: { familyMembers: true } }, assets: true },
    });
    if (!moment) throw new NotFoundException('记录不存在');
    const member = moment.baby.familyMembers.some(m => m.userId === user.id);
    if (user.role !== 'ADMIN' && (!member ||
        (moment.visibility === 'PRIVATE' && moment.authorId !== user.id) ||
        (moment.visibility === 'PARENTS_ONLY' && user.role !== 'PARENT'))) throw new ForbiddenException('无权访问此记录');
    if (edit && user.role !== 'ADMIN' && (user.role !== 'PARENT' || moment.authorId !== user.id)) {
      throw new ForbiddenException('只能管理自己的记录');
    }
    return moment;
  }
  async detail(user: AuthenticatedUser, id: string) {
    await this.accessible(user, id);
    const moment = await this.prisma.moment.findUniqueOrThrow({ where: { id }, include: {
      author: { select: { id: true, nickname: true, avatar: true } },
      assets: { orderBy: { sortOrder: 'asc' } },
      baby: { select: { id: true, name: true, birthday: true } },
      _count: { select: { likes: true, comments: true } },
      likes: { where: { userId: user.id }, select: { id: true } },
    } });
    const { likes, ...data } = moment;
    const members = await this.prisma.familyMember.findMany({where:{babyId:moment.babyId}});
    const names = new Map(members.map(m=>[m.userId,m.relationship]));
    return { success: true, data: { ...data, likedByMe: likes.length > 0,
      author: {...data.author,nickname:Array.from(new Set([moment.authorId,...moment.contributorIds].map(id=>names.get(id) || (id===moment.authorId ? data.author.nickname : '家人')))).join('、')},
      canEdit: user.role === 'ADMIN' || (user.role === 'PARENT' && moment.authorId === user.id) } };
  }
  async edit(user: AuthenticatedUser, id: string, dto: MomentEditDto) {
    const moment = await this.accessible(user, id, true);
    if (dto.coverAssetId && !moment.assets.some(a => a.immichAssetId === dto.coverAssetId)) throw new BadRequestException('封面必须来自这条记录');
    await this.prisma.$transaction(async tx => {
      await tx.moment.update({ where: { id }, data: {
        content: dto.content, location: dto.location,
        eventDate: dto.eventDate ? new Date(dto.eventDate) : undefined,
      } });
      if (dto.coverAssetId) {
        const ordered = moment.assets.sort((a,b) => a.sortOrder-b.sortOrder);
        ordered.sort((a,b) => Number(b.immichAssetId === dto.coverAssetId)-Number(a.immichAssetId === dto.coverAssetId));
        for (const [sortOrder, asset] of ordered.entries()) await tx.momentAsset.update({where:{id:asset.id},data:{sortOrder}});
      }
    });
    return this.detail(user,id);
  }
  async remove(user: AuthenticatedUser,id: string) {
    await this.accessible(user,id,true);
    await this.prisma.moment.delete({where:{id}});
    return {success:true,data:null};
  }
  async removeAsset(user: AuthenticatedUser, id: string, assetId: string) {
    await this.accessible(user, id, true);
    // Remove only this business association; never delete the original Immich media.
    await this.prisma.momentAsset.deleteMany({ where: { id: assetId, momentId: id } });
    return { success: true, data: null };
  }
  async comments(user: AuthenticatedUser,id: string,cursor?: string) {
    const moment = await this.accessible(user,id);
    const rows = await this.prisma.comment.findMany({ where:{momentId:id}, orderBy:[{createdAt:'desc'},{id:'desc'}],
      take:31, ...(cursor ? {cursor:{id:cursor},skip:1}:{}),
      include:{user:{select:{id:true,nickname:true}}} });
    const members = await this.prisma.familyMember.findMany({where:{babyId:moment.babyId}});
    const names = new Map(members.map(m=>[m.userId,m.relationship]));
    return {success:true,data:{items:rows.slice(0,30).map(c=>({...c,user:{...c.user,nickname:names.get(c.userId)||c.user.nickname}})),nextCursor:rows.length>30?rows[29].id:null}};
  }
  async comment(user: AuthenticatedUser,id: string,content: string) {
    await this.accessible(user,id);
    if (!content.trim()) throw new BadRequestException('评论不能为空');
    const data = await this.prisma.comment.create({data:{momentId:id,userId:user.id,content:content.trim()}});
    return {success:true,data};
  }
  async like(user: AuthenticatedUser,id: string,liked: boolean) {
    await this.accessible(user,id);
    if(liked) await this.prisma.like.upsert({where:{momentId_userId:{momentId:id,userId:user.id}},create:{momentId:id,userId:user.id},update:{}});
    else await this.prisma.like.deleteMany({where:{momentId:id,userId:user.id}});
    return this.detail(user,id);
  }
}
