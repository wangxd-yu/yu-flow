import React, { useMemo, useRef, useState } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { request } from '@umijs/max';
import { Button, Drawer, Empty, Space, Spin, Tabs, Tag, Typography, message } from 'antd';
import { ApiOutlined, CopyOutlined, EyeOutlined } from '@ant-design/icons';
import {
  LogDetailShell,
  LogCodePanel,
  LogDuration,
  LogResultTable,
  isTabularData,
  formatMaybeJson,
  prettyJson,
  safeParse,
} from '../shared';
import '@/styles/fullHeightTable.css';
import '../shared/logPageLayout.css';

const { Text } = Typography;

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

const METHOD_COLOR: Record<string, string> = {
  GET: 'green',
  POST: 'blue',
  PUT: 'orange',
  DELETE: 'red',
  PATCH: 'purple',
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

const statusColor = (status?: number) => {
  if (status == null) return 'default';
  if (status >= 200 && status < 300) return 'success';
  if (status >= 300 && status < 400) return 'processing';
  if (status >= 400 && status < 500) return 'warning';
  return 'error';
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

const ThirdLogPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [detailVisible, setDetailVisible] = useState(false);
  const [detail, setDetail] = useState<ThirdLogDTO | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('body');

  const formatted = useMemo(() => {
    if (!detail) {
      return { headers: '', params: '', body: '', curl: '' };
    }
    return {
      headers: formatMaybeJson(detail.requestHeaders),
      params: formatMaybeJson(detail.requestParams),
      body: formatMaybeJson(detail.responseBody),
      curl: detail.curl || '',
    };
  }, [detail]);

  const parsedBody = useMemo(
    () => (detail?.responseBody ? safeParse(detail.responseBody) : null),
    [detail?.responseBody],
  );
  const bodyTabular = useMemo(() => isTabularData(parsedBody), [parsedBody]);

  const openDetail = async (id: string) => {
    setDetailVisible(true);
    setDetailLoading(true);
    setDetail(null);
    // Response Body 最常用，默认打开
    setActiveTab('body');
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
      render: (_, record) => record.sourceName || '-',
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
      render: (_, record) => {
        const method = (record.requestMethod || '-').toUpperCase();
        return <Tag color={METHOD_COLOR[method] || 'default'}>{method}</Tag>;
      },
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
      width: 80,
      hideInSearch: true,
      render: (_, record) =>
        record.responseStatus == null ? (
          '-'
        ) : (
          <Tag color={statusColor(record.responseStatus)}>{record.responseStatus}</Tag>
        ),
    },
    {
      title: '结果',
      dataIndex: 'isSuccess',
      width: 80,
      valueType: 'select',
      valueEnum: {
        1: { text: '成功', status: 'Success' },
        0: { text: '失败', status: 'Error' },
      },
      render: (_, record) =>
        record.isSuccess === 1 ? (
          <Tag color="success">成功</Tag>
        ) : (
          <Tag color="error">失败</Tag>
        ),
    },
    {
      title: '耗时',
      dataIndex: 'elapsedTime',
      width: 100,
      hideInSearch: true,
      render: (_, record) => <LogDuration ms={record.elapsedTime} />,
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
        <Button
          key="detail"
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => openDetail(record.id)}
        >
          查看
        </Button>,
      ],
    },
  ];

  const sourceMeta = SOURCE_MAP[detail?.source || ''] || SOURCE_MAP.OTHER;
  const method = (detail?.requestMethod || '-').toUpperCase();

  const tabItems = detail
    ? [
        {
          key: 'body',
          label: 'Response Body',
          children: (
            <div className="log-detail-tab-pane">
              {parsedBody == null || parsedBody === '' ? (
                <Empty description="无响应体" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : bodyTabular ? (
                <Tabs
                  size="small"
                  defaultActiveKey="table"
                  destroyInactiveTabPane
                  tabBarStyle={{ marginBottom: 12 }}
                  items={[
                    {
                      key: 'table',
                      label: '表格视图',
                      children: (
                        <div className="log-detail-tab-pane-table">
                          <LogResultTable data={parsedBody} />
                        </div>
                      ),
                    },
                    {
                      key: 'raw',
                      label: '原始 JSON',
                      children: <LogCodePanel content={prettyJson(parsedBody)} />,
                    },
                  ]}
                />
              ) : (
                <LogCodePanel content={formatted.body || prettyJson(parsedBody)} emptyText="无响应体" />
              )}
            </div>
          ),
        },
        {
          key: 'params',
          label: 'Request Params',
          children: (
            <div className="log-detail-tab-pane">
              <LogCodePanel content={formatted.params} emptyText="无请求参数" />
            </div>
          ),
        },
        {
          key: 'headers',
          label: 'Request Headers',
          children: (
            <div className="log-detail-tab-pane">
              <LogCodePanel content={formatted.headers} emptyText="无请求头" />
            </div>
          ),
        },
        {
          key: 'curl',
          label: 'Curl',
          children: (
            <div className="log-detail-tab-pane">
              <LogCodePanel
                content={formatted.curl}
                language="text"
                emptyText="无 Curl"
              />
            </div>
          ),
        },
      ]
    : [];

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
          record.isSuccess === 0 ? 'log-row-fail' : ''
        }
        options={{
          density: true,
          fullScreen: true,
          reload: true,
          setting: true,
        }}
      />

      <Drawer
        title={`三方调用详情 - ${detail?.sourceName || detail?.apiType || ''}`}
        width="80%"
        open={detailVisible}
        onClose={() => {
          setDetailVisible(false);
          setDetail(null);
        }}
        destroyOnClose
        styles={{ body: { padding: 0 } }}
        extra={
          detail?.curl ? (
            <Button
              size="small"
              icon={<CopyOutlined />}
              onClick={() => copyText(detail.curl)}
            >
              复制 Curl
            </Button>
          ) : null
        }
      >
        <Spin spinning={detailLoading}>
          {detail && (
            <LogDetailShell
              overview={{
                title: detail.sourceName || detail.apiType || '未命名',
                icon: <ApiOutlined />,
                iconColor: '#1677ff',
                tags: (
                  <>
                    <Tag color={sourceMeta.color}>{sourceMeta.text}</Tag>
                    <Tag color={METHOD_COLOR[method] || 'default'} style={{ fontFamily: 'monospace' }}>
                      {method}
                    </Tag>
                    {detail.responseStatus != null && (
                      <Tag color={statusColor(detail.responseStatus)}>{detail.responseStatus}</Tag>
                    )}
                    <Text type="secondary" copyable style={{ fontSize: 13, fontFamily: 'monospace' }}>
                      {detail.requestUrl || '-'}
                    </Text>
                  </>
                ),
                success: detail.isSuccess === 1,
                successText: '调用成功',
                failText: '调用失败',
                durationMs: detail.elapsedTime,
                timeText: detail.createTime,
              }}
              errorMessage={detail.errorMessage}
              errorTitle="调用失败"
              activeTabKey={activeTab}
              onTabChange={setActiveTab}
              tabBarExtraContent={
                <Button
                  type="link"
                  size="small"
                  icon={<CopyOutlined />}
                  onClick={() => {
                    const map: Record<string, string | undefined> = {
                      headers: formatted.headers,
                      params: formatted.params,
                      body: formatted.body,
                      curl: formatted.curl,
                    };
                    copyText(map[activeTab]);
                  }}
                >
                  复制当前
                </Button>
              }
              tabItems={tabItems}
            />
          )}
        </Spin>
      </Drawer>
    </PageContainer>
  );
};

export default ThirdLogPage;
