import { Alert, List, Modal, Select, Space, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  checkPublishGate,
  listReleaseEnvs,
  type FlowEnv,
  type PublishGateResult,
} from '@/services/flow/releaseService';

type AssetType = 'API' | 'TASK' | 'SERVICE';

const statusColor = (s?: string) => {
  if (s === 'PASS') return 'success';
  if (s === 'FAIL') return 'error';
  return 'default';
};

/**
 * 发布前选择逻辑环境并预检门禁；通过后 resolve envCode，取消 resolve null。
 */
export function confirmPublishWithGate(options: {
  assetType: AssetType;
  assetId: string;
  assetName?: string;
}): Promise<string | null> {
  const { assetType, assetId, assetName } = options;

  return new Promise((resolve) => {
    const container = document.createElement('div');
    document.body.appendChild(container);
    const root = createRoot(container);

    const cleanup = (value: string | null) => {
      resolve(value);
      setTimeout(() => {
        root.unmount();
        container.remove();
      }, 0);
    };

    const Host = () => {
      const [envs, setEnvs] = useState<FlowEnv[]>([]);
      const [code, setCode] = useState('DEV');
      const [gate, setGate] = useState<PublishGateResult | null>(null);
      const [loading, setLoading] = useState(false);
      const [open, setOpen] = useState(true);
      const [submitting, setSubmitting] = useState(false);

      useEffect(() => {
        listReleaseEnvs()
          .then((list) => setEnvs(list?.length ? list : [{ id: 'dev', code: 'DEV', name: '开发' }]))
          .catch(() => setEnvs([{ id: 'dev', code: 'DEV', name: '开发' }]));
      }, []);

      useEffect(() => {
        let cancelled = false;
        setLoading(true);
        checkPublishGate({ assetType, assetId, envCode: code })
          .then((r) => {
            if (!cancelled) setGate(r);
          })
          .catch(() => {
            if (!cancelled) {
              setGate({
                assetType,
                assetId,
                envCode: code,
                passed: false,
                message: '门禁预检失败（可能无权限或后端未就绪）',
              });
            }
          })
          .finally(() => {
            if (!cancelled) setLoading(false);
          });
        return () => {
          cancelled = true;
        };
      }, [code]);

      const env = envs.find((e) => e.code === code);

      const close = (v: string | null) => {
        setOpen(false);
        cleanup(v);
      };

      return (
        <Modal
          open={open}
          title="发布确认（环境门禁）"
          width={560}
          okText="确认发布"
          cancelText="取消"
          confirmLoading={submitting || loading}
          okButtonProps={{ disabled: !gate?.passed }}
          onCancel={() => close(null)}
          onOk={async () => {
            setSubmitting(true);
            try {
              const latest = await checkPublishGate({ assetType, assetId, envCode: code });
              if (!latest.passed) {
                setGate(latest);
                throw new Error(latest.message || '门禁未通过');
              }
              close(code);
            } finally {
              setSubmitting(false);
            }
          }}
        >
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              将发布「{assetName || assetId}」的已保存草稿。预发/生产环境可能要求回归通过。
            </Typography.Paragraph>
            <div>
              <Typography.Text style={{ marginRight: 8 }}>发布环境</Typography.Text>
              <Select
                style={{ width: 260 }}
                value={code}
                onChange={setCode}
                options={envs.map((e) => ({
                  value: e.code,
                  label: `${e.name}（${e.code}）${e.requireSuitePass === 1 ? ' · 需回归' : ''}`,
                }))}
              />
            </div>
            {env?.requireSuitePass === 1 && (
              <Alert
                type="info"
                showIcon
                message={`该环境要求 ${env.passTtlHours || 24} 小时内回归 PASSED`}
              />
            )}
            <Alert
              type={gate?.passed ? 'success' : loading ? 'info' : 'warning'}
              showIcon
              message={loading ? '正在检查门禁…' : gate?.message || '门禁结果'}
            />
            <List
              size="small"
              bordered
              loading={loading}
              dataSource={gate?.checks || []}
              locale={{ emptyText: '暂无检查项' }}
              renderItem={(item) => (
                <List.Item>
                  <Space wrap>
                    <Tag color={statusColor(item.status)}>{item.status}</Tag>
                    <span>{item.name}</span>
                    <Typography.Text type="secondary">{item.message}</Typography.Text>
                  </Space>
                </List.Item>
              )}
            />
          </Space>
        </Modal>
      );
    };

    root.render(<Host />);
  });
}
