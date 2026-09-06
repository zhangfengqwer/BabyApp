import { UserRole } from '@prisma/client';

export type AuthenticatedUser = {
  id: string;
  username: string;
  nickname: string;
  role: UserRole;
};

export type TokenPayload = {
  sub: string;
  role: UserRole;
  type: 'access' | 'refresh';
};

