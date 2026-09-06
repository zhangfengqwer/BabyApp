import { HttpService } from '@nestjs/axios';
import { BadGatewayException, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Request, Response } from 'express';

@Injectable()
export class ImmichService {
  constructor(
    private readonly http: HttpService,
    private readonly config: ConfigService,
  ) {}

  async upload(request: Request, response: Response) {
    try {
      const upstream = await this.http.axiosRef.request({
        method: 'POST',
        url: `${this.baseUrl()}/api/assets`,
        data: request,
        headers: this.forwardHeaders(request, true),
        maxBodyLength: Infinity,
        maxContentLength: Infinity,
        timeout: 0,
        validateStatus: () => true,
      });
      response.status(upstream.status).send(upstream.data);
    } catch {
      throw new BadGatewayException('Immich 上传失败，请稍后重试');
    }
  }

  async view(assetId: string, size: string, request: Request, response: Response) {
    try {
      let path: string;
      let params: Record<string, string> | undefined;
      if (size === 'fullsize') {
        const metadata = await this.http.axiosRef.get(`${this.baseUrl()}/api/assets/${assetId}`, {
          headers: this.forwardHeaders(request, false),
          timeout: 10_000,
        });
        path = metadata.data.type === 'VIDEO'
          ? `/api/assets/${assetId}/video/playback`
          : `/api/assets/${assetId}/original`;
      } else {
        path = `/api/assets/${assetId}/thumbnail`;
        params = { size };
      }
      const upstream = await this.http.axiosRef.request({
        method: 'GET',
        url: `${this.baseUrl()}${path}`,
        params,
        headers: this.forwardHeaders(request, false),
        responseType: 'stream',
        timeout: 15_000,
        validateStatus: () => true,
      });
      for (const name of ['content-type', 'content-length', 'accept-ranges', 'content-range']) {
        const value = upstream.headers[name];
        if (value) response.setHeader(name, value);
      }
      response.status(upstream.status);
      upstream.data.pipe(response);
    } catch {
      throw new BadGatewayException('Immich 媒体读取失败');
    }
  }

  private baseUrl() {
    const value = this.config.get<string>('IMMICH_BASE_URL')?.trim().replace(/\/$/, '');
    if (!value) throw new Error('IMMICH_BASE_URL is not configured');
    return value;
  }

  private forwardHeaders(request: Request, includeBodyHeaders: boolean) {
    const apiKey = this.config.get<string>('IMMICH_API_KEY');
    if (!apiKey) throw new Error('IMMICH_API_KEY is not configured');
    const headers: Record<string, string> = { 'x-api-key': apiKey, accept: request.headers.accept ?? '*/*' };
    if (includeBodyHeaders && request.headers['content-type']) headers['content-type'] = request.headers['content-type'];
    if (includeBodyHeaders && request.headers['content-length']) headers['content-length'] = request.headers['content-length'];
    if (request.headers.range) headers.range = request.headers.range;
    return headers;
  }
}
