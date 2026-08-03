import { useEffect, useState } from 'react';
import { request } from '@umijs/max';

export function getLogModeLabel(mode?: string): string {
  switch (mode) {
    case 'ALL':
      return '全量记录';
    case 'OFF':
      return '完全关闭';
    case 'ERROR_ONLY':
      return '仅报错时记录';
    case 'SYSTEM_DEFAULT':
    default:
      return '仅报错时记录';
  }
}

export function useGlobalLogMode(): string {
  const [globalMode, setGlobalMode] = useState<string>('ERROR_ONLY');

  useEffect(() => {
    let active = true;
    request('/flow-api/sys-configs/key/ENGINE_DEFAULT_LOG_MODE', { method: 'GET' })
      .then((res: any) => {
        const val = typeof res === 'string' ? res : res?.data;
        if (active && typeof val === 'string' && val && val !== '/') {
          setGlobalMode(val);
        }
      })
      .catch(() => {
        // 忽略权限或静默失败，保留默认值
      });
    return () => {
      active = false;
    };
  }, []);

  return globalMode;
}
