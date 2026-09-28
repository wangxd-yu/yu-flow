import { useEffect, useState } from 'react';
import { getCurrentEnv } from '@/services/flow/releaseService';

/**
 * 本实例是否锁定资产编辑（yu.flow.release.lock-asset-editing）。
 * 锁定时资产变更只能走「版本发布 → 导入发布包」，页面上的旧导入入口应隐藏。
 */
export default function useEditLocked(): boolean {
  const [locked, setLocked] = useState(false);
  useEffect(() => {
    let alive = true;
    getCurrentEnv().then((env) => {
      if (alive) setLocked(!!env?.editLocked);
    });
    return () => {
      alive = false;
    };
  }, []);
  return locked;
}
