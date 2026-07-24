import { Alert, Modal, Select, Space, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { listReleaseEnvs, type FlowEnv } from '@/services/flow/releaseService';

/**
 * 批量回归前选择环境；确认返回 envCode，取消返回 null。
 */
export function confirmBatchRegression(options: {
  count: number;
  assetLabel?: string;
}): Promise<string | null> {
  const { count, assetLabel = '接口' } = options;

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
      const [code, setCode] = useState('STAGING');
      const [open, setOpen] = useState(true);

      useEffect(() => {
        listReleaseEnvs()
          .then((list) => {
            const next = list?.length ? list : [{ id: 'dev', code: 'DEV', name: '开发' }];
            setEnvs(next);
            if (!next.find((e) => e.code === 'STAGING') && next[0]) {
              setCode(next[0].code);
            }
          })
          .catch(() => setEnvs([{ id: 'dev', code: 'DEV', name: '开发' }]));
      }, []);

      const env = envs.find((e) => e.code === code);

      return (
        <Modal
          open={open}
          title="批量回归"
          okText="开始运行"
          cancelText="取消"
          onCancel={() => {
            setOpen(false);
            cleanup(null);
          }}
          onOk={() => {
            setOpen(false);
            cleanup(code);
          }}
        >
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              将对已选 {count} 个{assetLabel}依次运行启用中的首个回归套件（单次最多 20 个）。
              无套件的资产默认跳过。执行走内部引擎，不会外连 URL。
            </Typography.Paragraph>
            <div>
              <Typography.Text style={{ marginRight: 8 }}>目标环境</Typography.Text>
              <Select
                style={{ width: 260 }}
                value={code}
                onChange={setCode}
                options={envs.map((e) => ({
                  value: e.code,
                  label: `${e.name}（${e.code}）${e.requireSuitePass === 1 ? ' · 门禁相关' : ''}`,
                }))}
              />
            </div>
            {env?.requireSuitePass === 1 && (
              <Alert
                type="info"
                showIcon
                message={`在 ${env.code} 下 PASSED 的结果可用于该环境发布门禁（有效 ${env.passTtlHours || 24} 小时）`}
              />
            )}
          </Space>
        </Modal>
      );
    };

    root.render(<Host />);
  });
}
