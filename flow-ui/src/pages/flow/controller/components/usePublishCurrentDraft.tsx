/**
 * usePublishCurrentDraft
 * ─────────────────────────────────────────────────────────────────────────────
 * 保存当前草稿并执行发布门禁 / 发布流程。
 */
import { useCallback } from 'react';
import { Modal, message } from 'antd';
import { publishApi, republishApi } from '@/services/flow/flowController';
import { confirmPublishWithGate } from '@/components/flow/release/confirmPublishWithGate';
import type { EngineMode } from './panels/ImplementationPanel';

type SubmitResult = { success: boolean; id?: string };
type HandleSubmit = (externalScript?: any, options?: { notify?: boolean; closeOnSuccess?: boolean }) => Promise<SubmitResult>;

export interface UsePublishCurrentDraftOptions {
  isEdit: boolean;
  publishStatus: 0 | 1;
  name: string;
  interceptMode: 'REPLACE' | 'WRAP';
  engineMode: EngineMode;
  method: string;
  url: string;
  hostRouteExists: boolean;
  onSubmit: (success: boolean) => void;
  handleSubmit: HandleSubmit;
}

export function usePublishCurrentDraft(options: UsePublishCurrentDraftOptions) {
  const {
    isEdit,
    publishStatus,
    name,
    interceptMode,
    engineMode,
    method,
    url,
    hostRouteExists,
    onSubmit,
    handleSubmit,
  } = options;

  const handlePublishCurrentDraft = useCallback(async () => {
    const effectiveIntercept = interceptMode === 'WRAP' || engineMode === 'HOST' ? 'WRAP' : 'REPLACE';
    // 仅当宿主确有同 method+path 时强警告（避免无冲突路径也弹危险确认）
    if (effectiveIntercept === 'REPLACE' && hostRouteExists) {
      const ok = await new Promise<boolean>((resolve) => {
        Modal.confirm({
          title: '确认发布「同名替换」？',
          content: (
            <div>
              <p>检测到宿主已注册 <code>{method} {url}</code>。</p>
              <p>发布后，Yu Flow 将<strong>接管</strong>该路径，宿主同名 Controller <strong>不再被调用</strong>。</p>
              <p style={{ color: 'rgba(0,0,0,0.45)', marginBottom: 0 }}>若只需监控/限流原接口，请改用「同名包裹」模式。</p>
            </div>
          ),
          okText: '确认替换并发布',
          okButtonProps: { danger: true },
          cancelText: '取消',
          onOk: () => resolve(true),
          onCancel: () => resolve(false),
        });
      });
      if (!ok) return;
    }

    const saved = await handleSubmit(undefined, { notify: false, closeOnSuccess: false });
    if (!saved.success || !saved.id) {
      return;
    }

    const envCode = await confirmPublishWithGate({
      assetType: 'API',
      assetId: saved.id,
      assetName: name || saved.id,
    });
    if (!envCode) {
      return;
    }

    const isRepublish = isEdit && publishStatus === 1;
    const hide = message.loading(isRepublish ? '正在发布更新...' : '正在发布...');
    try {
      if (isRepublish) {
        await republishApi(saved.id, envCode);
      } else {
        await publishApi(saved.id, envCode);
      }
      hide();
      message.success(isRepublish ? '发布更新成功' : '发布成功');
      onSubmit(true);
    } catch (e) {
      hide();
    }
  }, [handleSubmit, isEdit, publishStatus, onSubmit, name, interceptMode, engineMode, method, url, hostRouteExists]);

  return handlePublishCurrentDraft;
}
