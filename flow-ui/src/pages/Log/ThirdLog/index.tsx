import React, { useRef, useState } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { request } from '@umijs/max';
import { Badge, Button, Drawer, Space, Spin, Tag, Typography, message } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  CopyOutlined,
} from '@ant-design/icons';

const { Text, Paragraph } = Typography;

const API_BASE = '/flow-api/log/third';

interface ThirdLogListDTO {
  id: string;
  apiType?: string;
  source?: string;
  sourceRef?: string;
  sourceName?: string;
  requestUrl?: string;
  requestMethod?: string;
  responseStatus?: number;
  elapsedTime?: number;
  isSuccess?: number;
  createTime?: string;
}

interface ThirdLogDTO extends ThirdLogListDTO {
  requestParams?: string;
  requestHeaders?: string;
  responseBody?: string;
  errorMessage?: string;
  curl?: string;
}

const SOURCE_MAP: Record<string, { color: string; text: string }> = {
  API: { color: 'blue', text: '接口调用' },
  TASK: { color: 'purple', text: '任务调用' },
  DEBUG: { color: 'orange', text: '调试运行' },
  OTHER: { color: 'default', text: '其他' },
};

const queryThirdLogPage = async (params: any) => {
  const { current, pageSize, createTime, ...rest } = params;

  let startTime: string | undefined;
  let endTime: string | undefined;
  if (createTime && Array.isArray(createTime)) {
    startTime = createTime[0];
    endTime = createTime[1];
  }

  const result = await request(`${API_BASE}/page`, {
    method: 'GET',
    params: {
      ...rest,
      startTime,
      endTime,
      page: (current || 1) - 1,
      size: pageSize || 20,
    },
  });
  return {
    data: result.items || [],
    success: true,
    total: result.total || 0,
  };
};

const getThirdLogDetail = async (id: string): Promise<ThirdLogDTO> => {
  return await request(`${API_BASE}/${id}`);
};

const formatDuration = (ms?: number) => {
  if (ms == null) return '-';
  if (ms < 1000) return `${ms} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
};

const copyText = async (text?: string) => {
  if (!text) {
    message.warning('无内容可复制');
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
    message.success('已复制');
  } catch {
    message.error('复制失败');
  }
};

const PreBlock: React.FC<{ title: string; content?: string; copyable?: boolean }> = ({
  title,
  content,
  copyable,
}) => (
  <div style={{ marginBottom: 16 }}>
    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
      <Text strong>{title}</Text>
      {copyable && (
        <Button
          type="link"
          size="small"
          icon={<CopyOutlined />}
          onClick={() => copyText(content)}
        >
          复制
        </Button>
      )}
    </div>
    <pre
      style={{
        background: '#f5f5f5',
        border: '1px solid #f0f0f0',
        borderRadius: 4,
        padding: 12,
        fontSize: 12,
        maxHeight: 280,
        overflow: 'auto',
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-all',
        margin: 0,
      }}
    >
      {content || '-'}
    </pre>
  </div>
);

const ThirdLogPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [detailVisible, setDetailVisible] = useState(false);
  const [detail, setDetail] = useState<ThirdLogDTO | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const openDetail = async (id: string) => {
    setDetailVisible(true);
    setDetailLoading(true);
    try {
      const data = await getThirdLogDetail(id);
      setDetail(data);
    } catch {
      message.error('加载详情失败');
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  };

  const columns: ProColumns<ThirdLogListDTO>[] = [
    {
      title: '来源',
      dataIndex: 'source',
      width: 110,
      valueType: 'select',
      valueEnum: {
        API: { text: '接口调用' },
        TASK: { text: '任务调用' },
        DEBUG: { text: '调试运行' },
        OTHER: { text: '其他' },
      },
      render: (_, record) => {
        const s = SOURCE_MAP[record.source || ''] || SOURCE_MAP.OTHER;
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
    {
      title: '来源对象',
      dataIndex: 'sourceName',
      width: 200,
      ellipsis: true,
      render: (_, record) => {
        if (!record.sourceName && !record.sourceRef) return '-';
        return (
          <Space direction="vertical" size={0}>
            <Text strong>{record.sourceName || '-'}</Text>
            {record.sourceRef && (
              <Text type="secondary" style={{ fontSize: 11 }} copyable>
                {record.sourceRef}
              </Text>
            )}
          </Space>
        );
      },
    },
    {
      title: '接口标识',
      dataIndex: 'apiType',
      width: 160,
      ellipsis: true,
    },
    {
      title: '方法',
      dataIndex: 'requestMethod',
      width: 90,
      valueType: 'select',
      valueEnum: {
        GET: { text: 'GET' },
        POST: { text: 'POST' },
        PUT: { text: 'PUT' },
        DELETE: { text: 'DELETE' },
        PATCH: { text: 'PATCH' },
      },
      render: (_, record) => (
        <Tag color="geekblue">{record.requestMethod || '-'}</Tag>
      ),
    },
    {
      title: '请求 URL',
      dataIndex: 'requestUrl',
      ellipsis: true,
      copyable: true,
    },
    {
      title: '状态码',
      dataIndex: 'responseStatus',
      width: 90,
      hideInSearch: true,
      render: (_, record) => record.responseStatus ?? '-',
    },
    {
      title: '是否成功',
      dataIndex: 'isSuccess',
      width: 110,
      valueType: 'select',
      valueEnum: {
        1: { text: '成功', status: 'Success' },
        0: { text: '失败', status: 'Error' },
      },
      render: (_, record) =>
        record.isSuccess === 1 ? (
          <Badge status="success" text={<Space size={4}><CheckCircleOutlined />成功</Space>} />
        ) : (
          <Badge status="error" text={<Space size={4}><CloseCircleOutlined />失败</Space>} />
        ),
    },
    {
      title: '耗时',
      dataIndex: 'elapsedTime',
      width: 100,
      hideInSearch: true,
      render: (_, record) => formatDuration(record.elapsedTime),
    },
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 170,
      valueType: 'dateTimeRange',
      search: {
        transform: (value) => ({ createTime: value }),
      },
      render: (_, record) => record.createTime || '-',
    },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      render: (_, record) => [
        <a key="detail" onClick={() => openDetail(record.id)}>
          详情
        </a>,
      ],
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
      header={{
        title: '三方日志',
        subTitle: '监控与追溯第三方接口调用状态、耗时及请求上下文',
      }}
    >
      <ProTable<ThirdLogListDTO>
        className="fh-table"
        rowKey="id"
        actionRef={actionRef}
        headerTitle="第三方接口调用日志"
        tableLayout="fixed"
        columns={columns}
        request={queryThirdLogPage}
        search={{
          labelWidth: 'auto',
          defaultCollapsed: false,
        }}
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
        }}
        scroll={{ x: 1200, y: 100000 }}
        rowClassName={(record) =>
          record.isSuccess === 0 ? 'third-log-row-fail' : ''
        }
        options={{
          density: true,
          fullScreen: true,
          reload: true,
          setting: true,
        }}
      />

      <Drawer
        title="三方日志详情"
        width={720}
        open={detailVisible}
        onClose={() => {
          setDetailVisible(false);
          setDetail(null);
        }}
        destroyOnClose
      >
        <Spin spinning={detailLoading}>
        {detail && (
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            <div>
              <Text type="secondary">来源：</Text>
              <Tag color={(SOURCE_MAP[detail.source || ''] || SOURCE_MAP.OTHER).color}>
                {(SOURCE_MAP[detail.source || ''] || SOURCE_MAP.OTHER).text}
              </Tag>
            </div>
            <div>
              <Text type="secondary">来源对象：</Text>
              <Text strong>{detail.sourceName || '-'}</Text>
              {detail.sourceRef && (
                <Text type="secondary" style={{ marginLeft: 8 }} copyable>
                  ID={detail.sourceRef}
                </Text>
              )}
            </div>
            <div>
              <Text type="secondary">接口标识：</Text>
              <Text>{detail.apiType || '-'}</Text>
            </div>
            <div>
              <Text type="secondary">请求：</Text>
              <Tag>{detail.requestMethod}</Tag>
              <Paragraph copyable style={{ display: 'inline', marginBottom: 0 }}>
                {detail.requestUrl}
              </Paragraph>
            </div>
            <div>
              <Text type="secondary">状态：</Text>
              {detail.isSuccess === 1 ? (
                <Badge status="success" text="成功" />
              ) : (
                <Badge status="error" text="失败" />
              )}
              <Text style={{ marginLeft: 12 }}>HTTP {detail.responseStatus ?? '-'}</Text>
              <Text style={{ marginLeft: 12 }}>耗时 {formatDuration(detail.elapsedTime)}</Text>
            </div>
            {detail.errorMessage && (
              <div>
                <Text type="danger">错误：{detail.errorMessage}</Text>
              </div>
            )}
            <PreBlock title="Request Headers" content={detail.requestHeaders} />
            <PreBlock title="Request Params" content={detail.requestParams} />
            <PreBlock title="Response Body" content={detail.responseBody} />
            <PreBlock title="Curl" content={detail.curl} copyable />
          </Space>
        )}
        </Spin>
      </Drawer>

      <style>{`
        .fh-container.ant-pro-page-container {
          display: flex !important;
          flex-direction: column !important;
        }
        .fh-container.ant-pro-page-container > .ant-pro-grid-content,
        .fh-container.ant-pro-page-container .ant-pro-grid-content-children {
          flex: 1 !important;
          min-height: 0 !important;
          display: flex !important;
          flex-direction: column !important;
        }
        .fh-container.ant-pro-page-container .ant-pro-page-container-children-container {
          flex: 1 !important;
          min-height: 0 !important;
          display: flex !important;
          flex-direction: column !important;
          height: auto !important;
          padding-block-end: 0 !important;
        }
        .fh-table.ant-pro-table {
          display: flex;
          flex-direction: column;
          height: 100%;
          overflow: hidden;
        }
        .fh-table .ant-pro-table-search {
          flex-shrink: 0;
        }
        .fh-table > .ant-pro-card:not(.ant-pro-table-search) {
          flex: 1;
          min-height: 0;
          display: flex;
          flex-direction: column;
        }
        .fh-table > .ant-pro-card:not(.ant-pro-table-search) > .ant-pro-card-body {
          flex: 1;
          min-height: 0;
          display: flex !important;
          flex-direction: column;
          overflow: hidden;
        }
        .fh-table .ant-pro-table-list-toolbar {
          flex-shrink: 0;
        }
        .fh-table .ant-table-wrapper {
          flex: 1;
          min-height: 0;
          display: flex;
          flex-direction: column;
        }
        .fh-table .ant-spin-nested-loading {
          flex: 1;
          min-height: 0;
          display: flex;
          flex-direction: column;
        }
        .fh-table .ant-spin-container {
          flex: 1;
          min-height: 0;
          display: flex;
          flex-direction: column;
        }
        .fh-table .ant-table {
          flex: 1;
          min-height: 0;
          display: flex;
          flex-direction: column;
        }
        .fh-table .ant-table-container {
          flex: 1;
          min-height: 0;
          display: flex;
          flex-direction: column;
        }
        .fh-table .ant-table-header {
          flex-shrink: 0;
          overflow: hidden !important;
        }
        .fh-table .ant-table-body {
          flex: 1;
          min-height: 0;
          max-height: none !important;
          overflow-y: scroll !important;
        }
        .fh-table .ant-table-pagination {
          flex-shrink: 0;
          padding: 6px 0;
          margin: 0 !important;
        }
        .third-log-row-fail td {
          background-color: #fff2f0 !important;
        }
        .third-log-row-fail:hover td {
          background-color: #ffebe8 !important;
        }
      `}</style>
    </PageContainer>
  );
};

export default ThirdLogPage;
