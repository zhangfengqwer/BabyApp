import { IsString, Length } from 'class-validator';

export class LoginDto {
  @IsString()
  @Length(1, 64)
  username: string;

  @IsString()
  @Length(1, 128)
  password: string;
}

