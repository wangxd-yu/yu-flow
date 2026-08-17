/**
 * 批量导出资产包。
 *
 * 典型用法：开发 / 测试环境验证完成后勾选资产导出，再到生产环境导入。
 * 包内保留源环境 ID，不含任何连接串与密钥；数据源 / MQ / OSS 按 code 引用，
 * 目标环境需自行准备同 code 的资源（导入预检会列出缺失项）。
 */
import React, { useState } from 'react';
import { ModalForm, ProFormRadio, ProFormSwitch, ProFormText } from '@ant-design/pro-components';
import { Alert, Modal, Typography, message } from 'antd';
import {
  downloadBundle,
  exportAssetBundle,
  type AssetExportPayload,
} from '@/services/flow/assetTransfer';

interface Props {
  open: boolean;
  /** 勾选的资产类型，决定 ID 放进哪个字段 */
  assetType: 'API' | 'SERVICE' | 'TASK';
  ids: string[];
  onCancel: () => void;
}

type ExportFormValues = {
  contentSource: string;
  includeDependencies: boolean;
  includeRegression: boolean;
  sourceEnv?: string;
};

const TYPE_LABELS: Record<Props['assetType'], string> = {
  API: '接口',
  SERVICE: '内部服务',
  TASK: '定时任务',
};

const FILE_PREFIX: Record<Props['assetType'], string> = {
  API: 'yu-flow-apis',
  SERVICE: 'yu-flow-services',
  TASK: 'yu-flow-tasks',
};

const AssetExportModal: React.FC<Props> = ({ open, assetType, ids, onCancel }) => {
  const [submitting, setSubmitting] = useState(false);

  return (
    <ModalForm<ExportFormValues>
      title={`导出${TYPE_LABELS[assetType]}（已选 ${ids.length} 项）`}
      width={560}
      open={open}
      onOpenChange={(visible) => {
        if (!visible) onCancel();
      }}
      modalProps={{ destroyOnClose: true, okText: '导出并下载', confirmLoading: submitting }}
      initialValues={{
        contentSource: 'PUBLISHED_FIRST',
        includeDependencies: true,
        includeRegression: true,
      }}
      onFinish={async (values) => {
        if (!ids.length) {
          message.warning('请先勾选要导出的资产');
          return false;
        }
        const payload: AssetExportPayload = {
          contentSource: values.contentSource,
          includeDependencies: values.includeDependencies,
          includeRegression: values.includeRegression,
          sourceEnv: values.sourceEnv?.trim() || undefined,
        };
        if (assetType === 'API') payload.apiIds = ids;
        if (assetType === 'SERVICE') payload.serviceIds = ids;
        if (assetType === 'TASK') payload.taskIds = ids;

        setSubmitting(true);
        try {
          const bundle = await exportAssetBundle(payload);
          if (!bundle) {
            message.error('导出失败，请重试');
            return false;
          }
          downloadBundle(bundle, FILE_PREFIX[assetType]);
          const counts = [
            `接口 ${bundle.apis?.length || 0}`,
            `内部服务 ${bundle.services?.length || 0}`,
            `定时任务 ${bundle.tasks?.length || 0}`,
            `目录 ${bundle.directories?.length || 0}`,
            `回归套件 ${bundle.regressionSuites?.length || 0}`,
          ].join(' / ');
          message.success(`导出成功：${counts}`);
          if (bundle.warnings?.length) {
            Modal.info({
              title: '导出提示',
              width: 620,
              content: (
                <ul style={{ paddingLeft: 18, marginBottom: 0 }}>
                  {bundle.warnings.map((w, idx) => (
                    <li key={idx}>
                      <Typography.Text>{w}</Typography.Text>
                    </li>
                  ))}
                </ul>
              ),
            });
          }
          return true;
        } catch (error: any) {
          if (!error?.message) message.error('导出失败，请重试');
          return false;
        } finally {
          setSubmitting(false);
        }
      }}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="包内不含数据库连接串与密钥"
        description="数据源 / MQ / OSS 都按 code 引用，目标环境需已存在同 code 的资源；导入预检会列出缺失项。"
      />
      <ProFormRadio.Group
        name="contentSource"
        label="导出内容"
        radioType="button"
        options={[
          { label: '已发布版本优先', value: 'PUBLISHED_FIRST' },
          { label: '当前草稿', value: 'DRAFT' },
        ]}
        extra="已发布版本优先：取线上快照内容，即测试环境验证通过的那一版；未发布的资产自动退回草稿。"
      />
      <ProFormSwitch
        name="includeDependencies"
        label="自动补齐依赖"
        extra="编排中调用到的其它接口 / 内部服务一并导出，避免目标环境断链。"
      />
      <ProFormSwitch
        name="includeRegression"
        label="包含回归用例"
        extra="生产环境发布门禁默认要求回归通过，不带的话导入后可能无法发布。"
      />
      <ProFormText
        name="sourceEnv"
        label="来源环境备注"
        placeholder="例如 TEST，仅写入包头供人识别"
      />
    </ModalForm>
  );
};

export default AssetExportModal;
