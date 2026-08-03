import { request } from '@umijs/max';

export type OssDownloadResult = 'SUCCESS' | 'DENIED' | 'NOT_FOUND' | 'ERROR';

export interface OssDownloadLog {
  id: string;
  objectId?: string;
  downloadedBy?: string;
  downloadedByName?: string;
  clientIp?: string;
  userAgent?: string;
  result?: OssDownloadResult;
  denyReason?: string;
  timeMs?: number;
  createTime?: string;
}

export async function queryOssDownloadLogPage(params: any) {
  return request<API.PageInfo<OssDownloadLog>>('/flow-api/oss/download-logs/page', {
    method: 'GET',
    params,
  });
}
