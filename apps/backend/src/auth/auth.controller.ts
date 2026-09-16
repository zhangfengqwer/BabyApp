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
  home(@Headers('x-home-access-key') accessKey?: string, @Headers('x-family-username') username?: string) {
    return this.auth.home(accessKey, username);
  }

  @Post('web-home')
  webHome(@Req() request: Request, @Headers('x-family-username') username?: string) {
    return this.auth.webHome(request.socket.remoteAddress, username);
  }

  @Post('refresh')
  refresh(@Body() dto: RefreshDto) {
    return this.auth.refresh(dto.refreshToken);
  }
}
