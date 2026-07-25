import React, { useEffect, useState } from 'react';
import { Modal, Table, Tag, message, Space } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  listHostApiRoutes,
  importHostApiRoutes,
  type HostApiRoute,
} from '@/services/flow/flowController';

interface Props {
  open: boolean;
  directoryId?: string;
  onCancel: () => void;
  onImported: () => void;
}

const HostApiImportModal: React.FC<Props> = ({ open, directoryId, onCancel, onImported }) => {
  const [loading, setLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [rows, setRows] = useState<HostApiRoute[]>([]);
  const [selected, setSelected] = useState<HostApiRoute[]>([]);

  useEffect(() => {
    if (!open) return;
    setSelected([]);
    setLoading(true);
    listHostApiRoutes()
      .then((res: any) => {
        const list = Array.isArray(res) ? res : (res?.data ?? []);
        setRows(list);
      })
      .catch(() => message.error('扫描宿主路由失败'))
      .finally(() => setLoading(false));
  }, [open]);

  const columns: ColumnsType<HostApiRoute> = [
    {
      title: '方法',
      dataIndex: 'method',
      width: 88,
      render: (m) => <Tag>{m}</Tag>,
    },
    { title: '路径', dataIndex: 'path', ellipsis: true },
    {
      title: 'Handler',
      width: 200,
      ellipsis: true,
      render: (_, r) => `${r.handlerClass || ''}.${r.handlerMethod || ''}`,
    },
    {
      title: '状态',
      width: 120,
      render: (_, r) =>
        r.managed ? (
          <Tag color="blue">已纳管</Tag>
        ) : (
          <Tag color="cyan">可导入</Tag>
        ),
    },
  ];

  const handleOk = async () => {
    const items = selected.filter((r) => !r.managed);
    if (!items.length) {
      message.warning('请选择尚未纳管的宿主路由');
      return;
    }
    setImporting(true);
    try {
      const res: any = await importHostApiRoutes({
        directoryId,
        items: items.map((i) => ({ method: i.method, path: i.path })),
      });
      const created = res?.created ?? res?.data?.created ?? 0;
      message.success(`已导入 ${created} 条 WRAP 草稿（未发布）`);
      onImported();
    } catch {
      /* interceptor */
    } finally {
      setImporting(false);
    }
  };

  return (
    <Modal
      title="从宿主导入 API"
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      okText="导入为包裹草稿"
      confirmLoading={importing}
      width={820}
      destroyOnClose
    >
      <Space direction="vertical" style={{ width: '100%' }} size={8}>
        <div style={{ color: 'rgba(0,0,0,0.45)', fontSize: 13 }}>
          扫描宿主 MVC 路由（已排除 /flow-api、/flow-ui）。导入后为<strong>未发布</strong>的同名包裹草稿，需手动发布才生效。
        </div>
        <Table<HostApiRoute>
          rowKey={(r) => `${r.method} ${r.path}`}
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={{ pageSize: 8, showSizeChanger: false }}
          rowSelection={{
            selectedRowKeys: selected.map((r) => `${r.method} ${r.path}`),
            onChange: (_keys, sel) => setSelected(sel),
            getCheckboxProps: (r) => ({ disabled: !!r.managed }),
          }}
          scroll={{ y: 360 }}
        />
      </Space>
    </Modal>
  );
};

export default HostApiImportModal;
