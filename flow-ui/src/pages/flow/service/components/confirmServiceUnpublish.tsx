import React from 'react';
import { Modal } from 'antd';
import { listServiceFlowReferences } from '../services/serviceFlowService';

/**
 * 下线前软确认：有引用时列出调用方，用户确认后仍可继续下线。
 * @returns true 表示用户确认下线
 */
export async function confirmServiceUnpublish(
  serviceId: string,
  serviceName?: string,
): Promise<boolean> {
  let refs: string[] = [];
  try {
    refs = await listServiceFlowReferences(serviceId);
  } catch {
    // 引用查询失败时仍允许走确认框，避免阻断下线
  }

  const titleName = serviceName ? `「${serviceName}」` : '该服务';

  if (!refs.length) {
    return new Promise((resolve) => {
      Modal.confirm({
        title: `确认下线${titleName}？`,
        content: '下线后，其他流程将无法通过 CALL 调用此服务。',
        okText: '确认下线',
        cancelText: '取消',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
  }

  return new Promise((resolve) => {
    Modal.confirm({
      title: `服务仍被引用，确认下线${titleName}？`,
      width: 520,
      content: (
        <div>
          <p style={{ marginBottom: 8 }}>
            下线后以下调用方将无法再 CALL 本服务：
          </p>
          <ul style={{ margin: 0, paddingLeft: 20, maxHeight: 240, overflow: 'auto' }}>
            {refs.map((label) => (
              <li key={label}>{label}</li>
            ))}
          </ul>
        </div>
      ),
      okText: '仍要下线',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}
