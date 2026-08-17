import React, { useCallback, useEffect, useState } from 'react';
import { Button, Drawer, Space, Table, Tag, Tooltip, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { HistoryOutlined, RollbackOutlined } from '@ant-design/icons';

export interface AssetVersionItem {
  id: string;
  versionNo: number;
  source?: string;
  remark?: string;
  publisher?: string;
  publishTime?: string;
  current?: boolean;
}

export interface AssetVersionHistoryDrawerProps {
  open: boolean;
  onClose: () => void;
  /** 资产展示名 */
  title?: string;
  loadVersions: () => Promise<AssetVersionItem[]>;
  restoreVersion: (versionId: string) => Promise<void>;
  /** 回退后刷新外层表单 */
  onRestored?: () => void;
  /** 只读：无写权限时仅查看版本记录，不展示回退操作 */
  readOnly?: boolean;
}

const SOURCE_LABEL: Record<string, { text: string; color: string }> = {
  publish: { text: '发布', color: 'blue' },
  rollback: { text: '回退', color: 'orange' },
};

/**
 * 三端共用：历史版本抽屉。
 * 「回退」会同步覆盖草稿与线上快照（published_snapshot）。
 */
const AssetVersionHistoryDrawer: React.FC<AssetVersionHistoryDrawerProps> = ({
  open,
  onClose,
  title,
  loadVersions,
  restoreVersion,
  onRestored,
  readOnly = false,
}) => {
  const [loading, setLoading] = useState(false);
  const [restoringId, setRestoringId] = useState<string | null>(null);
  const [rows, setRows] = useState<AssetVersionItem[]>([]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const list = await loadVersions();
      setRows(Array.isArray(list) ? list : []);
    } catch (e: any) {
      message.error(e?.message || '加载历史版本失败');
    } finally {
      setLoading(false);
    }
  }, [loadVersions]);

  useEffect(() => {
    if (open) {
      refresh();
    }
  }, [open, refresh]);

  const handleRestore = async (record: AssetVersionItem) => {
    if (record.current) {
      message.info('已是当前线上版本');
      return;
    }
    setRestoringId(record.id);
    try {
      await restoreVersion(record.id);
      message.success(`已回退至 v${record.versionNo}（线上与草稿已同步）`);
      await refresh();
      onRestored?.();
    } catch (e: any) {
      message.error(e?.message || '回退失败');
    } finally {
      setRestoringId(null);
    }
  };

  const columns: ColumnsType<AssetVersionItem> = [
    {
      title: '版本',
      dataIndex: 'versionNo',
      width: 90,
      render: (v, r) => (
        <Space size={4}>
          <span>v{v}</span>
          {r.current ? <Tag color="success">线上</Tag> : null}
        </Space>
      ),
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 90,
      render: (s: string) => {
        const meta = SOURCE_LABEL[s] || { text: s || '-', color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '发布时间',
      dataIndex: 'publishTime',
      width: 170,
    },
    {
      title: '发布人',
      dataIndex: 'publisher',
      width: 100,
      ellipsis: true,
      render: (t) => t || '-',
    },
    {
      title: '备注',
      dataIndex: 'remark',
      ellipsis: true,
      render: (t) => t || '-',
    },
  ];

  if (!readOnly) {
    columns.push({
      title: '操作',
      width: 120,
      render: (_, record) => (
        <Tooltip
          title={
            record.current
              ? '当前线上版本'
              : '回退将同时覆盖线上快照与编辑草稿为该历史版本'
          }
        >
          <Button
            type="link"
            size="small"
            icon={<RollbackOutlined />}
            disabled={!!record.current}
            loading={restoringId === record.id}
            onClick={() => handleRestore(record)}
          >
            回退
          </Button>
        </Tooltip>
      ),
    });
  }

  return (
    <Drawer
      title={title ? `历史版本 · ${title}` : '历史版本'}
      width={800}
      open={open}
      onClose={onClose}
      destroyOnClose
    >
      <Table<AssetVersionItem>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: '暂无历史版本，发布后将自动记录' }}
      />
    </Drawer>
  );
};

export default AssetVersionHistoryDrawer;

/** 表单工具栏上的「历史版本」入口按钮 */
export const HistoryVersionButton: React.FC<{
  disabled?: boolean;
  onClick: () => void;
  size?: 'small' | 'middle' | 'large';
}> = ({ disabled, onClick, size = 'small' }) => (
  <Button size={size} icon={<HistoryOutlined />} disabled={disabled} onClick={onClick}>
    历史版本
  </Button>
);
