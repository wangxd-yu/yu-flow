import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { Button, Divider, Dropdown, message, Popconfirm, Tag } from 'antd';
import type { MenuProps } from 'antd';
import React, { useRef, useState } from 'react';
import OssIntegrationAlert from '@/components/flow/OssIntegrationAlert';
import ThumbCell from '@/pages/flow/ossObject/components/ThumbCell';
import {
  deleteOssObject,
  downloadOssObjectContent,
  OssObject,
  packDownloadOssObjects,
  presignOssObject,
  queryOssObjectPage,
} from '@/services/flow/ossObject';

import '@/styles/fullHeightTable.css';

function formatBytes(size?: number) {
  if (size == null || size < 0) return '-';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`;
  return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

const OssObjectList: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);

  const handleDownload = async (record: OssObject, mode: 'stream' | 'presign') => {
    if (record.visibility === 'PUBLIC' && record.publicPath) {
      window.open(record.publicPath, '_blank');
      return;
    }
    const hide = message.loading('正在下载...', 0);
    try {
      if (mode === 'presign') {
        const presign = await presignOssObject(record.id);
        window.open(presign.url, '_blank');
        message.success('已打开预签名下载链接');
      } else {
        await downloadOssObjectContent(record.id, record.originalName || 'download');
        message.success('下载已开始');
      }
    } catch (e: any) {
      if (!e?.message?.includes('DEMO_RESTRICTED')) {
        message.error(e?.message || '下载失败');
      }
    } finally {
      hide();
    }
  };

  const downloadMenu = (record: OssObject): MenuProps['items'] => [
    { key: 'stream', label: '流式下载', onClick: () => handleDownload(record, 'stream') },
    { key: 'presign', label: '预签名下载', onClick: () => handleDownload(record, 'presign') },
  ];

  const columns: ProColumns<OssObject>[] = [
    {
      title: '缩略图',
      dataIndex: 'thumbStatus',
      width: 72,
      search: false,
      render: (_, record) => (
        <ThumbCell
          objectId={record.id}
          thumbStatus={record.thumbStatus}
          alt={record.originalName}
        />
      ),
    },
    {
      title: '原始文件名',
      dataIndex: 'originalName',
      width: 220,
      ellipsis: true,
    },
    {
      title: '场景编码',
      dataIndex: 'profileCode',
      width: 140,
      ellipsis: true,
    },
    {
      title: '可见性',
      dataIndex: 'visibility',
      width: 90,
      valueEnum: {
        PUBLIC: { text: '公有' },
        PRIVATE: { text: '私有' },
      },
      render: (_, record) => (
        <Tag color={record.visibility === 'PUBLIC' ? 'blue' : 'purple'} style={{ margin: 0 }}>
          {record.visibility === 'PUBLIC' ? '公有' : '私有'}
        </Tag>
      ),
    },
    {
      title: '大小',
      dataIndex: 'sizeBytes',
      width: 100,
      search: false,
      render: (_, record) => formatBytes(record.sizeBytes),
    },
    {
      title: '过期时间',
      dataIndex: 'expiresAt',
      valueType: 'dateTime',
      width: 160,
      search: false,
    },
    {
      title: '上传人',
      dataIndex: 'uploadedByName',
      width: 120,
      ellipsis: true,
      search: false,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      search: false,
      valueEnum: {
        ACTIVE: { text: '正常', status: 'Success' },
        DELETED: { text: '已删除', status: 'Default' },
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      valueType: 'dateTime',
      width: 160,
      search: false,
    },
    {
      title: '操作',
      dataIndex: 'option',
      valueType: 'option',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <span style={{ display: 'inline-flex', alignItems: 'center', whiteSpace: 'nowrap' }}>
          {record.visibility === 'PRIVATE' ? (
            <Dropdown menu={{ items: downloadMenu(record) }} trigger={['click']}>
              <a onClick={(e) => e.preventDefault()}>下载</a>
            </Dropdown>
          ) : (
            <a onClick={() => handleDownload(record, 'stream')}>下载</a>
          )}
          {record.status === 'ACTIVE' ? (
            <>
              <Divider type="vertical" key="d1" />
              <Popconfirm
                key="delete"
                title="确定要删除该文件吗？（软删除，后台异步清理存储）"
                onConfirm={async () => {
                  try {
                    await deleteOssObject(record.id);
                    message.success('已删除');
                    actionRef.current?.reload();
                  } catch {
                    /* ignore */
                  }
                }}
              >
                <a>删除</a>
              </Popconfirm>
            </>
          ) : null}
        </span>
      ),
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
      header={{
        title: 'OSS 文件',
      }}
    >
      <OssIntegrationAlert />
      <ProTable<OssObject>
        className="fh-table"
        headerTitle="OSS 文件列表"
        actionRef={actionRef}
        rowKey="id"
        tableLayout="fixed"
        scroll={{ x: 1200, y: 100000 }}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => setSelectedRowKeys(keys as string[]),
        }}
        toolBarRender={() => [
          <Button
            key="pack"
            disabled={selectedRowKeys.length === 0}
            onClick={async () => {
              const hide = message.loading('正在打包...', 0);
              try {
                await packDownloadOssObjects(selectedRowKeys);
                message.success('打包下载已开始');
              } catch (e: any) {
                message.error(e?.message || '打包失败');
              } finally {
                hide();
              }
            }}
          >
            批量打包下载
          </Button>,
        ]}
        search={{
          labelWidth: 120,
        }}
        request={async (params = {}) => {
          const { current, pageSize, ...restParams } = params as any;
          const result = await queryOssObjectPage({
            ...restParams,
            page: (current || 1) - 1,
            size: pageSize || 20,
          });
          const data = (result as any)?.data || result;
          return {
            data: data?.items || [],
            success: true,
            total: data?.total,
          };
        }}
        columns={columns}
      />
    </PageContainer>
  );
};

export default OssObjectList;
