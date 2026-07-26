/**
 * useInterceptModeChange
 * ─────────────────────────────────────────────────────────────────────────────
 * 切换「同名替换 / 同名包裹」模式，含危险操作二次确认。
 */
import React, { useCallback } from 'react';
import { Modal } from 'antd';
import { parseHostBinding, type HostWrapBinding } from './panels/HostWrapConfig';
import type { EngineMode } from './panels/ImplementationPanel';

export interface UseInterceptModeChangeOptions {
  interceptMode: 'REPLACE' | 'WRAP';
  setInterceptMode: (v: 'REPLACE' | 'WRAP') => void;
  setEngineMode: React.Dispatch<React.SetStateAction<EngineMode>>;
  setHostBinding: (v: React.SetStateAction<HostWrapBinding>) => void;
}

export function useInterceptModeChange(options: UseInterceptModeChangeOptions) {
  const { interceptMode, setInterceptMode, setEngineMode, setHostBinding } = options;

  const handleInterceptModeChange = useCallback((next: 'REPLACE' | 'WRAP') => {
    if (next === interceptMode) return;
    if (next === 'REPLACE') {
      Modal.confirm({
        title: '切换为「同名替换」？',
        content: '替换模式下需配置 FLOW/DB 等实现；发布后宿主同名接口将不可达。',
        okText: '切换为替换',
        okButtonProps: { danger: true },
        onOk: () => {
          setInterceptMode('REPLACE');
          setEngineMode((m) => (m === 'HOST' ? 'FLOW' : m));
        },
      });
      return;
    }
    Modal.confirm({
      title: '切换为「同名包裹」？',
      content: '包裹模式将转发至宿主原接口，Yu Flow 仅做增强（日志/计量/可选防护）。现有引擎实现内容不会用于线上。',
      okText: '切换为包裹',
      onOk: () => {
        setInterceptMode('WRAP');
        setEngineMode('HOST');
        setHostBinding((prev) => ({
          ...parseHostBinding(),
          ...prev,
          probeEnabled: prev.probeEnabled ?? true,
          logMode: prev.logMode || 'ERROR_ONLY',
        }));
      },
    });
  }, [interceptMode, setInterceptMode, setEngineMode, setHostBinding]);

  return handleInterceptModeChange;
}
