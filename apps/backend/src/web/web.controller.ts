import { Controller, Get, Redirect } from '@nestjs/common';

@Controller()
export class WebController {
  @Get()
  @Redirect('/web/', 302)
  root() {
    return undefined;
  }
}
