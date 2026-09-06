import { PrismaClient, UserRole } from '@prisma/client';
import { hash } from 'bcryptjs';

const prisma = new PrismaClient();

async function main() {
  const username = process.env.INITIAL_ADMIN_USERNAME?.trim() || 'admin';
  const password = process.env.INITIAL_ADMIN_PASSWORD;
  if (!password || password.length < 10 || password === 'change-this-before-seeding') {
    throw new Error('请在 .env 中设置至少 10 位的 INITIAL_ADMIN_PASSWORD');
  }
  const passwordHash = await hash(password, 12);
  const nickname = process.env.INITIAL_ADMIN_NICKNAME?.trim() || '管理员';
  const admin = await prisma.user.upsert({
    where: { username },
    update: { nickname, passwordHash, role: UserRole.ADMIN },
    create: {
      username,
      nickname,
      passwordHash,
      role: UserRole.ADMIN,
    },
  });

  const babyName = process.env.INITIAL_BABY_NAME?.trim();
  const birthdayValue = process.env.INITIAL_BABY_BIRTHDAY?.trim();
  if (babyName && birthdayValue) {
    const birthday = new Date(`${birthdayValue}T00:00:00.000Z`);
    if (Number.isNaN(birthday.getTime())) throw new Error('INITIAL_BABY_BIRTHDAY 格式必须为 YYYY-MM-DD');
    let baby = await prisma.baby.findFirst({ where: { name: babyName, birthday } });
    baby ??= await prisma.baby.create({ data: { name: babyName, birthday } });
    await prisma.familyMember.upsert({
      where: { babyId_userId: { babyId: baby.id, userId: admin.id } },
      update: {},
      create: { babyId: baby.id, userId: admin.id, relationship: '管理员' },
    });
    const momentCount = await prisma.moment.count({ where: { babyId: baby.id } });
    if (momentCount === 0) {
      await prisma.moment.create({
        data: {
          babyId: baby.id,
          authorId: admin.id,
          content: '今天第一次自己穿鞋 👟',
          eventDate: new Date(),
        },
      });
    }
  }
  console.log(`初始管理员已准备：${username}`);
}

main()
  .finally(() => prisma.$disconnect())
  .catch((error) => {
    console.error(error instanceof Error ? error.message : error);
    process.exit(1);
  });
