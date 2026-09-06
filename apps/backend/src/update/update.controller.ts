import {
  Controller,
  Get,
  NotFoundException,
  Res,
  UseGuards,
} from '@nestjs/common';
import { Response } from 'express';
import { readFile, stat } from 'node:fs/promises';
import { join } from 'node:path';
import { AuthGuard } from '../auth/auth.guard';

type ReleaseManifest = {
  versionCode: number;
  versionName: string;
  sha256: string;
  publishedAt: string;
};

const releaseDirectory = process.env.APP_RELEASE_DIR ?? '/app/releases';
const manifestPath = join(releaseDirectory, 'version.json');
const apkPath = join(releaseDirectory, 'zhizhi-growth-latest.apk');

@Controller('app')
@UseGuards(AuthGuard)
export class UpdateController {
  @Get('version')
  async version() {
    try {
      const manifest = JSON.parse(await readFile(manifestPath, 'utf8')) as ReleaseManifest;
      const apk = await stat(apkPath);
      return {
        success: true,
        data: {
          ...manifest,
          sizeBytes: apk.size,
          downloadPath: '/app/apk',
        },
      };
    } catch {
      throw new NotFoundException('暂未发布可用的安装包');
    }
  }

  @Get('apk')
  async apk(@Res() response: Response) {
    try {
      await stat(apkPath);
      response.download(apkPath, 'zhizhi-growth-latest.apk');
    } catch {
      throw new NotFoundException('安装包不存在');
    }
  }
}
