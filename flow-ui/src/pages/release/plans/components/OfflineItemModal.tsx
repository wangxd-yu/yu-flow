import React from 'react';
import { ModalForm, ProFormDependency, ProFormSelect, ProFormText } from '@ant-design/pro-components';
import { Alert, message } from 'antd';
import {
  addOfflineItems,
  OFFLINE_TYPES,
  RELEASE_ASSET_LABELS,
  type Release,
  type ReleaseAssetType,
} from '@/services/flow/releasePlan';

interface Props {
  open: boolean;
  releaseId: string;
  onCancel: () => void;
  onAdded: (release: Release) => void;
}

/**
 * 手工加入下线项：资产在本环境可能已删除，因此按 ID 填写（开放平台按编码）。
 */
const OfflineItemModal: React.FC<Props> = ({ open, releaseId, onCancel, onAdded }) => (
  <ModalForm<{ assetType: ReleaseAssetType; assetId: string; assetName?: string; assetKey?: string }>
    title="加入下线项"
    width={520}
    open={open}
    modalProps={{ destroyOnClose: true, onCancel }}
    onOpenChange={(v) => !v && onCancel()}
    initialValues={{ assetType: 'API' }}
    onFinish={async (values) => {
      const release = await addOfflineItems(releaseId, [values]);
      message.success('已加入下线项');
      onAdded(release);
      return true;
    }}
  >
    <Alert
      type="info"
      showIcon
      style={{ marginBottom: 16 }}
      message="目标环境导入时只撤销发布 / 停用，不删除数据；已删除的资产请从上个版本单或导入记录里查到 ID。"
    />
    <ProFormSelect
      name="assetType"
      label="类型"
      rules={[{ required: true }]}
      options={OFFLINE_TYPES.map((t) => ({ value: t, label: RELEASE_ASSET_LABELS[t] }))}
    />
    <ProFormText name="assetId" label="资产 ID" rules={[{ required: true, message: '请填写资产 ID' }]} />
    <ProFormDependency name={['assetType']}>
      {({ assetType }) =>
        assetType === 'OPEN_PLATFORM' ? (
          <ProFormText
            name="assetKey"
            label="平台编码"
            tooltip="开放平台在各环境 ID 不同，目标环境按编码匹配"
            rules={[{ required: true, message: '请填写平台编码' }]}
          />
        ) : null
      }
    </ProFormDependency>
    <ProFormText name="assetName" label="名称" placeholder="便于运维识别，可不填" />
  </ModalForm>
);

export default OfflineItemModal;
