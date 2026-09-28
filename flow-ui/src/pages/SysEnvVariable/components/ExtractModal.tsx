import React, { useEffect, useState } from 'react';
import { Alert, Button, Input, List, Modal, Popover, Space, Tag, Typography, message } from 'antd';
import {
  extractEnvVariable,
  listExtractCandidates,
  type ExtractCandidate,
} from '@/services/flow/envVariable';

interface Props {
  open: boolean;
  onCancel: () => void;
  onDone: () => void;
}

const TYPE_LABELS: Record<string, string> = { API: '接口', SERVICE: '内部服务', TASK: '定时任务', MQ_TASK: 'MQ 任务' };

/**
 * 从编排里抽取写死的第三方地址：创建变量并把 httpRequest 节点的 URL 前缀替换为 ${env.变量名}。
 */
const ExtractModal: React.FC<Props> = ({ open, onCancel, onDone }) => {
  const [list, setList] = useState<ExtractCandidate[]>([]);
  const [codes, setCodes] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [applying, setApplying] = useState<string>();

  const load = async () => {
    setLoading(true);
    try {
      const data = await listExtractCandidates();
      setList(data);
      setCodes(Object.fromEntries(data.map((c) => [c.baseUrl, c.suggestedCode])));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open) load();
  }, [open]);

  const apply = async (c: ExtractCandidate) => {
    const code = (codes[c.baseUrl] || '').trim().toUpperCase();
    setApplying(c.baseUrl);
    try {
      const r = await extractEnvVariable({ baseUrl: c.baseUrl, code });
      message.success(
        `${r.variableCreated ? '已创建变量' : '沿用已有变量'} ${r.code}，改写 ${r.updatedAssets} 个资产 / ${r.updatedNodes} 个节点（草稿），重新发布后生效`,
      );
      onDone();
      await load();
    } finally {
      setApplying(undefined);
    }
  };

  return (
    <Modal title="从编排提取地址" width={900} open={open} onCancel={onCancel} footer={null} destroyOnClose>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="扫描接口 / 服务 / 任务草稿里 httpRequest 节点写死的地址，按主机归组。提取后该地址作为本环境的变量值，节点 URL 改为 ${env.变量名}。"
        description="只修改草稿，需要重新发布才生效；其它环境导入时会提示补充这个变量。"
      />
      <List<ExtractCandidate>
        loading={loading}
        dataSource={list}
        locale={{ emptyText: '没有发现写死的地址' }}
        renderItem={(c) => (
          <List.Item
            actions={[
              <Button key="apply" type="primary" size="small" loading={applying === c.baseUrl} onClick={() => apply(c)}>
                提取
              </Button>,
            ]}
          >
            <Space direction="vertical" size={4} style={{ width: '100%' }}>
              <Space wrap>
                <Typography.Text code>{c.baseUrl}</Typography.Text>
                <Popover
                  title="引用位置"
                  content={
                    <div style={{ maxHeight: 280, overflow: 'auto', maxWidth: 520 }}>
                      {c.usages.map((u, i) => (
                        <div key={i}>
                          {TYPE_LABELS[u.assetType] || u.assetType}「{u.assetName}」 <Typography.Text type="secondary">{u.url}</Typography.Text>
                        </div>
                      ))}
                    </div>
                  }
                >
                  <Tag color="blue" style={{ cursor: 'pointer' }}>
                    {c.usages.length} 处引用
                  </Tag>
                </Popover>
              </Space>
              <Space>
                <Typography.Text type="secondary">变量名</Typography.Text>
                <Input
                  size="small"
                  style={{ width: 320 }}
                  value={codes[c.baseUrl]}
                  onChange={(e) => setCodes({ ...codes, [c.baseUrl]: e.target.value.toUpperCase() })}
                />
                {c.codeExists && <Tag color="warning">变量已存在，将沿用其值</Tag>}
              </Space>
            </Space>
          </List.Item>
        )}
      />
    </Modal>
  );
};

export default ExtractModal;
