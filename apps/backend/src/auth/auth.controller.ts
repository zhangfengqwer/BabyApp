import { Body, Controller, Headers, Post, Req } from '@nestjs/common';
import { Request } from 'express';
import { AuthService } from './auth.service';
import { LoginDto } from './dto/login.dto';
import { RefreshDto } from './dto/refresh.dto';

@Controller('auth')
export class AuthController {
  constructor(private readonly auth: AuthService) {}

  @Post('login')
  login(@Body() dto: LoginDto) {
    return this.auth.login(dto);
  }

  @Post('home')
  home(@Headers('x-home-access-key') accessKey?: string) {
    return this.auth.home(accessKey);
  }

  @Post('web-home')
  webHome(@Req() request: Request) {
    return this.auth.webHome(request.socket.remoteAddress);
  }

  @Post('refresh')
  refresh(@Body() dto: RefreshDto) {
    return this.auth.refresh(dto.refreshToken);
  }
}
