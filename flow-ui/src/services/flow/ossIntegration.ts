import { request } from '@umijs/max';

export interface OssIntegrationStatus {
  principalProvider?: string;
  dataScopeProvider?: string;
  supportedScopes?: string[];
  hints?: string[];
}

export async function getOssIntegrationStatus(): Promise<OssIntegrationStatus> {
  const result = await request<OssIntegrationStatus>('/flow-api/oss/integration-status', {
    method: 'GET',
  });
  return (result as any)?.data ?? result;
}
