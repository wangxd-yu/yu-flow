/**
 * ApiDataViewOpenExportTab
 * ─────────────────────────────────────────────────────────────────────────────
 * ApiDataViewDrawer「对外下载」Tab：开放导出开关、URL 预览、签发签名链。
 */
import React from 'react';
import { Alert, Button, Descriptions, Form, InputNumber, Space, Switch, Tag, Typography } from 'antd';
import type { FormInstance } from 'antd/es/form';
import { CopyOutlined, LinkOutlined, SaveOutlined } from '@ant-design/icons';
import type { FlowController } from '@/services/flow/flowController';

export interface ApiDataViewOpenExportTabProps {
  cfgForm: FormInstance;
  detail: FlowController | null;
  openExportEnabled: boolean;
  signedLinkEnabled: boolean;
  signedLinkPreview: string | null;

  exportDirectUrl: string;
  exportOpenUrl: string;
  isPublished: boolean;
  dirty: boolean;
  dbOk: boolean;
  saving: boolean;
  publishing: boolean;
  signingLink: boolean;
  saveAndPublishExport: (opts?: { quiet?: boolean }) => Promise<boolean | undefined>;
  handleIssueSignedLink: () => void;
  saveColumnConfig: (override?: any, quiet?: boolean) => void;
  handleCopyUrl: (url: string) => void;
}

const ApiDataViewOpenExportTab: React.FC<ApiDataViewOpenExportTabProps> = ({
  cfgForm,
  detail,
  openExportEnabled,
  signedLinkEnabled,
  signedLinkPreview,
  exportDirectUrl,
  exportOpenUrl,
  isPublished,
  dirty,
  dbOk,
  saving,
  publishing,
  signingLink,
  saveAndPublishExport,
  handleIssueSignedLink,
  saveColumnConfig,
  handleCopyUrl,
}) => {
  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Alert
        type="info"
        showIcon
        message="对外地址为业务 path + /export，不改动原 JSON 契约"
        description="开启后点「保存并发布」即可对外；需要浏览器直链时再点签发（未就绪会自动保存并发布）。"
      />
      <Form form={cfgForm} layout="vertical" onValuesChange={() => {}}>
        <Form.Item
          name="openExportEnabled"
          label="启用对外 Excel 下载"
          valuePropName="checked"
          extra="仅 DB + PAGE/LIST/OBJECT；默认关闭"
        >
          <Switch disabled={!dbOk} />
        </Form.Item>
        <Form.Item
          name="signedLinkEnabled"
          label="允许签发短期下载链"
          valuePropName="checked"
          extra="浏览器直链，无需 AppKey"
        >
          <Switch disabled={!openExportEnabled} />
        </Form.Item>
        <Form.Item
          name="signedLinkTtlSeconds"
          label="短期链有效期（秒）"
          extra="默认 300，范围 30–3600"
        >
          <InputNumber min={30} max={3600} style={{ width: 160 }} disabled={!openExportEnabled} />
        </Form.Item>
      </Form>

      <Descriptions size="small" bordered column={1} title="导出 URL 预览">
        <Descriptions.Item label={`${(detail?.method || 'GET').toUpperCase()} 直连`}>
          <Space wrap>
            <Typography.Text code copyable={false} style={{ wordBreak: 'break-all' }}>
              {exportDirectUrl || '-'}
            </Typography.Text>
            <Button
              size="small"
              icon={<CopyOutlined />}
              disabled={!exportDirectUrl}
              onClick={() => handleCopyUrl(exportDirectUrl)}
            >
              复制
            </Button>
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label={`${(detail?.method || 'GET').toUpperCase()} 开放入口`}>
          <Space wrap>
            <Typography.Text code copyable={false} style={{ wordBreak: 'break-all' }}>
              {exportOpenUrl || '-'}
            </Typography.Text>
            <Button
              size="small"
              icon={<CopyOutlined />}
              disabled={!exportOpenUrl}
              onClick={() => handleCopyUrl(exportOpenUrl)}
            >
              复制
            </Button>
          </Space>
        </Descriptions.Item>
      </Descriptions>

      <Space wrap>
        <Button
          type="primary"
          icon={<SaveOutlined />}
          loading={saving || publishing}
          disabled={!openExportEnabled || !dbOk}
          onClick={() => saveAndPublishExport()}
        >
          保存并发布
        </Button>
        <Button
          icon={<LinkOutlined />}
          loading={signingLink || publishing}
          disabled={!openExportEnabled || !signedLinkEnabled || !dbOk}
          onClick={handleIssueSignedLink}
        >
          {!isPublished || dirty ? '保存发布并签发' : '签发下载链接'}
        </Button>
        <Button loading={saving} disabled={!dbOk} onClick={() => saveColumnConfig()}>
          仅保存草稿
        </Button>
      </Space>

      {signedLinkPreview && (
        <Alert
          type="success"
          showIcon
          message="最近签发的短期链"
          description={
            <Space direction="vertical" style={{ width: '100%' }}>
              <Typography.Text code style={{ wordBreak: 'break-all' }}>
                {signedLinkPreview}
              </Typography.Text>
              <Button
                size="small"
                icon={<CopyOutlined />}
                onClick={() => handleCopyUrl(signedLinkPreview)}
              >
                再次复制
              </Button>
            </Space>
          }
        />
      )}
    </Space>
  );
};

export default ApiDataViewOpenExportTab;
