import { Alert, Modal, Select, Space, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { listReleaseEnvs, type FlowEnv } from '@/services/flow/releaseService';

/**
 * 批量发布前选择目标环境；确认返回 envCode，取消返回 null。
 *
 * <p>逐个资产的门禁由后端 assertCanPublish 判定，未通过的会在结果汇总里单独列出，
 * 因此这里只选环境、不做逐个预检（资产可能有几十个）。</p>
 */
export function confirmBatchPublish(options: {
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
      const [code, setCode] = useState('DEV');
      const [open, setOpen] = useState(true);

      useEffect(() => {
        listReleaseEnvs()
          .then((list) => setEnvs(list?.length ? list : [{ id: 'dev', code: 'DEV', name: '开发' }]))
          .catch(() => setEnvs([{ id: 'dev', code: 'DEV', name: '开发' }]));
      }, []);

      const env = envs.find((e) => e.code === code);

      return (
        <Modal
          open={open}
          title="批量发布"
          okText="确认发布"
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
              将把已选 {count} 个{assetLabel}的已保存草稿依次发布到目标环境；
              门禁未通过或路径冲突的会被跳过并在结果中列出。
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
                type="warning"
                showIcon
                message={`该环境要求 ${env.passTtlHours || 24} 小时内回归 PASSED，未达标的接口会发布失败`}
              />
            )}
          </Space>
        </Modal>
      );
    };

    root.render(<Host />);
  });
}
