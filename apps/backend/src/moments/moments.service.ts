import { ForbiddenException, Injectable, NotFoundException, BadRequestException, ConflictException, InternalServerErrorException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { ImmichService } from '../immich/immich.service';
import { AuthenticatedUser } from '../auth/auth.types';
import { MomentEditDto } from './moments.controller';

@Injectable()
export class MomentsService {
  constructor(private readonly prisma: PrismaService, private readonly immich: ImmichService) {}
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
    const moment = await this.accessible(user,id,true);
    await this.deleteOriginals(moment.assets);
    try { await this.prisma.moment.delete({where:{id}}); }
    catch { throw new InternalServerErrorException('服务器原件已进入回收站，但记录同步失败，请联系管理员处理'); }
    return {success:true,data:null};
  }
  async removeAsset(user: AuthenticatedUser, id: string, assetId: string) {
    return this.removeAssets(user, id, [assetId]);
  }
  async removeAssets(user: AuthenticatedUser, id: string, assetIds: string[]) {
    const moment = await this.accessible(user, id, true);
    const uniqueIds = [...new Set(assetIds)];
    if (!uniqueIds.length || uniqueIds.some(assetId => !moment.assets.some(asset => asset.id === assetId))) {
      throw new BadRequestException('只能删除这条记录中的媒体');
    }
    const selected = moment.assets.filter(asset => uniqueIds.includes(asset.id));
    await this.deleteOriginals(selected);
    try { await this.prisma.momentAsset.deleteMany({ where: { momentId: id, id: { in: uniqueIds } } }); }
    catch { throw new InternalServerErrorException('服务器原件已进入回收站，但记录同步失败，请联系管理员处理'); }
    return { success: true, data: null };
  }
  private async deleteOriginals(assets: { id: string; immichAssetId: string }[]) {
    if (!assets.length) return;
    const linkIds = assets.map(asset => asset.id);
    const originalIds = [...new Set(assets.map(asset => asset.immichAssetId))];
    const [otherMoment, babyAvatar, milestoneCover, userAvatar] = await Promise.all([
      this.prisma.momentAsset.findFirst({ where: { immichAssetId: { in: originalIds }, id: { notIn: linkIds } }, select: { id: true } }),
      this.prisma.baby.findFirst({ where: { avatarAssetId: { in: originalIds } }, select: { id: true } }),
      this.prisma.milestone.findFirst({ where: { coverAssetId: { in: originalIds } }, select: { id: true } }),
      this.prisma.user.findFirst({ where: { avatar: { in: originalIds } }, select: { id: true } }),
    ]);
    if (otherMoment || babyAvatar || milestoneCover || userAvatar) {
      throw new ConflictException('选中的原件仍被其他记录或头像使用，请先移除其他引用');
    }
    await this.immich.deleteAssets(originalIds);
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
