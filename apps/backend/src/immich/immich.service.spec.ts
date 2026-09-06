import { ImmichService } from './immich.service';

describe('ImmichService media routing', () => {
  const config = {
    get: jest.fn((name: string) => ({
      IMMICH_BASE_URL: 'http://immich:2283',
      IMMICH_API_KEY: 'test-key',
    })[name]),
  };
  const request = { headers: { accept: '*/*', range: 'bytes=0-1023' } };
  const response = {
    setHeader: jest.fn(),
    status: jest.fn().mockReturnThis(),
  };

  beforeEach(() => jest.clearAllMocks());

  it('uses thumbnail endpoint for timeline images', async () => {
    const pipe = jest.fn();
    const axiosRef = {
      request: jest.fn().mockResolvedValue({ status: 200, headers: {}, data: { pipe } }),
    };
    const service = new ImmichService({ axiosRef } as never, config as never);

    await service.view('asset-id', 'thumbnail', request as never, response as never);

    expect(axiosRef.request).toHaveBeenCalledWith(expect.objectContaining({
      url: 'http://immich:2283/api/assets/asset-id/thumbnail',
      params: { size: 'thumbnail' },
    }));
    expect(pipe).toHaveBeenCalledWith(response);
  });

  it('uses transcoded playback endpoint for fullsize video', async () => {
    const pipe = jest.fn();
    const axiosRef = {
      get: jest.fn().mockResolvedValue({ data: { type: 'VIDEO' } }),
      request: jest.fn().mockResolvedValue({ status: 206, headers: {}, data: { pipe } }),
    };
    const service = new ImmichService({ axiosRef } as never, config as never);

    await service.view('video-id', 'fullsize', request as never, response as never);

    expect(axiosRef.request).toHaveBeenCalledWith(expect.objectContaining({
      url: 'http://immich:2283/api/assets/video-id/video/playback',
    }));
  });
});
