/**
 * SimpleTraceViewer.tsx
 * ═══════════════════════════════════════════════════════════════════════════
 * 非 FLOW 类型 API（DB / JSON / STRING）的执行快照查看器。
 *
 * 根据 serviceType 自动适配展示内容：
 *   - DB     → SQL 语句 + 表格化查询结果
 *   - JSON   → JSON 内容展示
 *   - STRING → 纯文本展示
 * ═══════════════════════════════════════════════════════════════════════════
 */

import React, { useMemo, useState } from 'react';
import {
  Typography, Tag, Table, Alert, Collapse, Card, Space, Badge, Tabs, Empty,
} from 'antd';
import {
  CheckCircleFilled, CloseCircleFilled, ClockCircleOutlined,
  DatabaseOutlined, FileTextOutlined, CodeOutlined, ApiOutlined,
  DownOutlined,
} from '@ant-design/icons';

const { Text, Title } = Typography;

// ═══════════════════════════════════════════════════════════════════════════
//  类型定义
// ═══════════════════════════════════════════════════════════════════════════

interface SimpleTraceViewerProps {
  /** 执行日志详情（含大字段） */
  detail: {
    apiName?: string;
    url?: string;
    method?: string;
    serviceType?: string;
    status?: string;
    costTimeMs?: number;
    createTime?: string;
    requestParams?: string;
    responseBody?: string;
    errorMsg?: string;
    traceData?: string;
  };
}

// ═══════════════════════════════════════════════════════════════════════════
//  工具函数
// ═══════════════════════════════════════════════════════════════════════════

/** 安全解析 JSON */
const safeParse = (str?: string): any => {
  if (!str) return null;
  try { return JSON.parse(str); } catch { return str; }
};

/** 安全格式化 JSON */
const prettyJson = (obj: any): string => {
  if (obj === undefined || obj === null) return '';
  if (typeof obj === 'string') {
    try { return JSON.stringify(JSON.parse(obj), null, 2); }
    catch { return obj; }
  }
  try { return JSON.stringify(obj, null, 2); } catch { return String(obj); }
};

/** 格式化耗时 */
const formatDuration = (ms?: number) => {
  if (ms == null) return '-';
  if (ms < 1000) return `${ms} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
};

/** 从 traceData 中提取合成的 stepLog */
const extractStepLog = (traceData?: string) => {
  if (!traceData) return null;
  try {
    const trace = JSON.parse(traceData);
    return trace?.stepLogs?.[0] || null;
  } catch { return null; }
};

/**
 * 智能拆分请求参数：分离业务参数与 HTTP 协议头。
 * 后端全量存储，前端只默认展示业务关键部分。
 */
const splitRequestParams = (raw: any): {
  businessParams: Record<string, any> | null;
  httpHeaders: Record<string, string> | null;
} => {
  if (!raw || typeof raw !== 'object') return { businessParams: raw, httpHeaders: null };

  const headers = raw.headers || null;
  const business: Record<string, any> = {};
  let hasBusiness = false;

  // 提取业务相关字段
  const businessKeys = ['params', 'body', 'queryParams', 'bodyParams', '@QP', '@BP', '@PP'];
  for (const key of businessKeys) {
    if (raw[key] !== undefined && raw[key] !== null) {
      // 跳过空对象 {}
      if (typeof raw[key] === 'object' && Object.keys(raw[key]).length === 0) continue;
      business[key] = raw[key];
      hasBusiness = true;
    }
  }

  // 也收集任何非标准的自定义字段（排除 headers 和已知的 businessKeys）
  const knownKeys = new Set([...businessKeys, 'headers']);
  for (const key of Object.keys(raw)) {
    if (!knownKeys.has(key)) {
      business[key] = raw[key];
      hasBusiness = true;
    }
  }

  return {
    businessParams: hasBusiness ? business : null,
    httpHeaders: headers && typeof headers === 'object' && Object.keys(headers).length > 0 ? headers : null,
  };
};

// ═══════════════════════════════════════════════════════════════════════════
//  子组件：代码块
// ═══════════════════════════════════════════════════════════════════════════

const CodeBlock: React.FC<{ content: string; language?: string; maxHeight?: number }> = ({
  content, maxHeight = 400,
}) => (
  <pre style={{
    background: '#1e1e2e',
    color: '#cdd6f4',
    padding: '16px 20px',
    borderRadius: 8,
    fontSize: 13,
    fontFamily: "'JetBrains Mono', 'Fira Code', Consolas, monospace",
    lineHeight: 1.6,
    maxHeight,
    overflow: 'auto',
    margin: 0,
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-all',
  }}>
    <code>{content}</code>
  </pre>
);

// ═══════════════════════════════════════════════════════════════════════════
//  子组件：动态结果表格（将 List/Page 数据渲染为 Ant Table）
// ═══════════════════════════════════════════════════════════════════════════

const ResultTable: React.FC<{ data: any }> = ({ data }) => {
  // 处理分页包装 { items: [...], total, ... }
  let rows: any[] = [];
  let pageInfo: { total?: number; page?: number; totalPage?: number } | null = null;

  if (Array.isArray(data)) {
    rows = data;
  } else if (data && typeof data === 'object') {
    if (Array.isArray(data.items)) {
      rows = data.items;
      pageInfo = { total: data.total, page: data.page, totalPage: data.totalPage };
    } else if (Array.isArray(data.content)) {
      rows = data.content;
      pageInfo = { total: data.total };
    } else if (Array.isArray(data.rows)) {
      rows = data.rows;
    } else {
      // 单个对象
      rows = [data];
    }
  }

  if (rows.length === 0) {
    return <Empty description="查询结果为空" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }

  // 从第一行数据推导列定义
  const columns = Object.keys(rows[0]).map((key) => ({
    title: key,
    dataIndex: key,
    key,
    ellipsis: true,
    render: (val: any) => {
      if (val === null || val === undefined) return <Text type="secondary">NULL</Text>;
      if (typeof val === 'object') return <Text code style={{ fontSize: 11 }}>{JSON.stringify(val)}</Text>;
      return String(val);
    },
  }));

  return (
    <div>
      {pageInfo && (
        <div style={{ marginBottom: 8 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            共 <Text strong>{pageInfo.total ?? rows.length}</Text> 条记录
            {pageInfo.totalPage != null && <>，共 {pageInfo.totalPage} 页</>}
          </Text>
        </div>
      )}
      <Table
        columns={columns}
        dataSource={rows.map((r, i) => ({ ...r, __key: i }))}
        rowKey="__key"
        size="small"
        bordered
        scroll={{ x: 'max-content', y: 400 }}
        pagination={rows.length > 50 ? { pageSize: 50, showSizeChanger: true } : false}
        style={{ fontSize: 12 }}
      />
    </div>
  );
};

// ═══════════════════════════════════════════════════════════════════════════
//  主组件
// ═══════════════════════════════════════════════════════════════════════════

const SimpleTraceViewer: React.FC<SimpleTraceViewerProps> = ({ detail }) => {
  const {
    apiName, url, method, serviceType, status,
    costTimeMs, createTime, requestParams, responseBody, errorMsg, traceData,
  } = detail;

  const stepLog = useMemo(() => extractStepLog(traceData), [traceData]);
  const parsedResponse = useMemo(() => safeParse(responseBody), [responseBody]);
  const parsedRequest = useMemo(() => safeParse(requestParams), [requestParams]);

  // 从 stepLog 中提取源内容
  const sourceContent = useMemo(() => {
    if (!stepLog?.inputs) return null;
    const inputs = stepLog.inputs;
    if (inputs.actualSql) return { type: 'SQL', content: inputs.actualSql };
    if (inputs.jsonContent) return { type: 'JSON', content: inputs.jsonContent };
    if (inputs.textContent) return { type: 'TEXT', content: inputs.textContent };
    return null;
  }, [stepLog]);

  // 判断响应结果是否可以表格化展示
  const isTabular = useMemo(() => {
    if (!parsedResponse) return false;
    if (Array.isArray(parsedResponse) && parsedResponse.length > 0 && typeof parsedResponse[0] === 'object') return true;
    if (parsedResponse.items && Array.isArray(parsedResponse.items)) return true;
    if (parsedResponse.content && Array.isArray(parsedResponse.content)) return true;
    if (parsedResponse.rows && Array.isArray(parsedResponse.rows)) return true;
    return false;
  }, [parsedResponse]);

  const serviceTypeLabel: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
    DB: { label: '数据库查询', color: '#722ed1', icon: <DatabaseOutlined /> },
    JSON: { label: '静态 JSON', color: '#13c2c2', icon: <CodeOutlined /> },
    STRING: { label: '静态文本', color: '#fa8c16', icon: <FileTextOutlined /> },
  };

  const typeInfo = serviceTypeLabel[serviceType || ''] || {
    label: serviceType || '未知', color: '#8c8c8c', icon: <ApiOutlined />,
  };

  return (
    <div style={{
      height: '100%',
      overflow: 'auto',
      background: 'linear-gradient(135deg, #f5f7fa 0%, #e4e9f2 100%)',
      padding: '24px 32px',
    }}>
      {/* ── 顶部概览卡片 ── */}
      <Card
        style={{
          marginBottom: 20,
          borderRadius: 12,
          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06)',
        }}
        bodyStyle={{ padding: '20px 24px' }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{
              width: 40, height: 40, borderRadius: 10,
              background: `linear-gradient(135deg, ${typeInfo.color}20, ${typeInfo.color}40)`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 20, color: typeInfo.color,
            }}>
              {typeInfo.icon}
            </div>
            <div>
              <Title level={4} style={{ margin: 0, fontSize: 18 }}>{apiName || '未命名接口'}</Title>
              <Space size={8} style={{ marginTop: 4 }}>
                <Tag color={typeInfo.color} style={{ borderRadius: 4 }}>{typeInfo.label}</Tag>
                <Tag color="blue" style={{ fontFamily: 'monospace', borderRadius: 4 }}>{method}</Tag>
                <Text type="secondary" copyable style={{ fontSize: 13, fontFamily: 'monospace' }}>{url}</Text>
              </Space>
            </div>
          </div>

          <Space size={16}>
            {status === 'SUCCESS' ? (
              <Badge status="success" text={
                <Tag icon={<CheckCircleFilled />} color="success" style={{ fontSize: 13, padding: '2px 12px' }}>
                  执行成功
                </Tag>
              } />
            ) : (
              <Badge status="error" text={
                <Tag icon={<CloseCircleFilled />} color="error" style={{ fontSize: 13, padding: '2px 12px' }}>
                  执行失败
                </Tag>
              } />
            )}
            <span style={{ color: costTimeMs != null && costTimeMs >= 1000 ? '#ff4d4f' : '#52c41a', fontWeight: 600 }}>
              <ClockCircleOutlined style={{ marginRight: 4 }} />
              {formatDuration(costTimeMs)}
            </span>
            {createTime && <Text type="secondary" style={{ fontSize: 12 }}>{createTime}</Text>}
          </Space>
        </div>
      </Card>

      {/* ── 错误信息 ── */}
      {errorMsg && (
        <Alert
          type="error"
          showIcon
          message="执行异常"
          description={
            <pre style={{
              margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all',
              fontFamily: "'JetBrains Mono', monospace", fontSize: 12, maxHeight: 200, overflow: 'auto',
            }}>
              {errorMsg}
            </pre>
          }
          style={{ marginBottom: 20, borderRadius: 10 }}
        />
      )}

      {/* ── 主要内容区 ── */}
      <Card
        style={{ borderRadius: 12, boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06)' }}
        bodyStyle={{ padding: 0 }}
      >
        <Tabs
          defaultActiveKey="result"
          style={{ padding: '0 4px' }}
          items={[
            // ── Tab: 执行结果（子 Tab：表格视图 / 原始 JSON） ──
            {
              key: 'result',
              label: (
                <span style={{ padding: '0 8px' }}>
                  <DatabaseOutlined style={{ marginRight: 6 }} />
                  执行结果
                </span>
              ),
              children: (
                <div style={{ padding: '16px 20px' }}>
                  {parsedResponse == null ? (
                    <Empty description="无返回数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  ) : isTabular ? (
                    /* 有表格数据时，提供子 Tab 切换 */
                    <Tabs
                      size="small"
                      defaultActiveKey="table"
                      tabBarStyle={{ marginBottom: 12 }}
                      items={[
                        {
                          key: 'table',
                          label: '📊 表格视图',
                          children: <ResultTable data={parsedResponse} />,
                        },
                        {
                          key: 'raw',
                          label: '📄 原始 JSON',
                          children: <CodeBlock content={prettyJson(parsedResponse)} maxHeight={600} />,
                        },
                      ]}
                    />
                  ) : (
                    /* 非表格数据，直接展示 JSON */
                    <CodeBlock content={prettyJson(parsedResponse)} />
                  )}
                </div>
              ),
            },
            // ── Tab: 源内容（SQL / JSON / 文本） ──
            ...(sourceContent ? [{
              key: 'source',
              label: (
                <span style={{ padding: '0 8px' }}>
                  <CodeOutlined style={{ marginRight: 6 }} />
                  {sourceContent.type === 'SQL' ? 'SQL 语句' : sourceContent.type === 'JSON' ? 'JSON 内容' : '文本内容'}
                </span>
              ),
              children: (
                <div style={{ padding: '16px 20px' }}>
                  <CodeBlock content={sourceContent.content} maxHeight={500} />
                </div>
              ),
            }] : []),
            // ── Tab: 请求参数（智能展示：业务参数 + 可折叠 HTTP 协议头） ──
            {
              key: 'request',
              label: (
                <span style={{ padding: '0 8px' }}>
                  <ApiOutlined style={{ marginRight: 6 }} />
                  请求参数
                </span>
              ),
              children: (() => {
                const { businessParams, httpHeaders } = splitRequestParams(parsedRequest);
                const hasAny = businessParams || httpHeaders;
                if (!hasAny) {
                  return (
                    <div style={{ padding: '16px 20px' }}>
                      <Empty description="无请求参数" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    </div>
                  );
                }
                return (
                  <div style={{ padding: '16px 20px' }}>
                    {/* 业务参数 */}
                    {businessParams ? (
                      <>
                        <div style={{ marginBottom: 8, fontSize: 13, fontWeight: 600, color: '#1f1f1f' }}>业务参数</div>
                        <CodeBlock content={prettyJson(businessParams)} />
                      </>
                    ) : (
                      <div style={{ marginBottom: 16, color: '#8c8c8c', fontSize: 13 }}>无业务参数</div>
                    )}

                    {/* HTTP 协议头（默认折叠） */}
                    {httpHeaders && (
                      <Collapse
                        ghost
                        style={{ marginTop: 16 }}
                        items={[{
                          key: 'headers',
                          label: (
                            <span style={{ fontSize: 13, color: '#8c8c8c' }}>
                              HTTP 请求头（{Object.keys(httpHeaders).length} 项）
                            </span>
                          ),
                          children: <CodeBlock content={prettyJson(httpHeaders)} maxHeight={300} />,
                        }]}
                      />
                    )}
                  </div>
                );
              })(),
            },
            // ── Tab: 原始 Trace ──
            {
              key: 'trace',
              label: (
                <span style={{ padding: '0 8px' }}>
                  <FileTextOutlined style={{ marginRight: 6 }} />
                  原始 Trace
                </span>
              ),
              children: (
                <div style={{ padding: '16px 20px' }}>
                  {traceData ? (
                    <CodeBlock content={prettyJson(safeParse(traceData))} maxHeight={600} />
                  ) : (
                    <Empty description="无 Trace 数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  )}
                </div>
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
};

export default SimpleTraceViewer;
