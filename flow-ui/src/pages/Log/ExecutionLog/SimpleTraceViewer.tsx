/**
 * SimpleTraceViewer.tsx
 * 非 FLOW 类型 API（DB / JSON / STRING）的执行快照查看器。
 */
import React, { useMemo } from 'react';
import { Typography, Tag, Collapse, Tabs, Empty } from 'antd';
import {
  DatabaseOutlined, FileTextOutlined, CodeOutlined, ApiOutlined,
} from '@ant-design/icons';
import {
  LogDetailShell,
  LogCodePanel,
  LogResultTable,
  isTabularData,
  safeParse,
  prettyJson,
} from '../shared';

const { Text } = Typography;

interface SimpleTraceViewerProps {
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

const splitRequestParams = (raw: any): {
  businessParams: Record<string, any> | null;
  httpHeaders: Record<string, string> | null;
} => {
  if (!raw || typeof raw !== 'object') return { businessParams: raw, httpHeaders: null };

  const headers = raw.headers || null;
  const business: Record<string, any> = {};
  let hasBusiness = false;

  const businessKeys = ['params', 'body', 'queryParams', 'bodyParams', '@QP', '@BP', '@PP'];
  for (const key of businessKeys) {
    if (raw[key] !== undefined && raw[key] !== null) {
      if (typeof raw[key] === 'object' && Object.keys(raw[key]).length === 0) continue;
      business[key] = raw[key];
      hasBusiness = true;
    }
  }

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

const extractStepLog = (traceData?: string) => {
  if (!traceData) return null;
  try {
    const trace = JSON.parse(traceData);
    return trace?.stepLogs?.[0] || null;
  } catch {
    return null;
  }
};

const SimpleTraceViewer: React.FC<SimpleTraceViewerProps> = ({ detail }) => {
  const {
    apiName, url, method, serviceType, status,
    costTimeMs, createTime, requestParams, responseBody, errorMsg, traceData,
  } = detail;

  const stepLog = useMemo(() => extractStepLog(traceData), [traceData]);
  const parsedResponse = useMemo(() => safeParse(responseBody), [responseBody]);
  const parsedRequest = useMemo(() => safeParse(requestParams), [requestParams]);

  const sourceContent = useMemo(() => {
    if (!stepLog?.inputs) return null;
    const inputs = stepLog.inputs;
    if (inputs.actualSql) return { type: 'SQL', content: inputs.actualSql, language: 'sql' as const };
    if (inputs.jsonContent) return { type: 'JSON', content: inputs.jsonContent, language: 'json' as const };
    if (inputs.textContent) return { type: 'TEXT', content: inputs.textContent, language: 'text' as const };
    return null;
  }, [stepLog]);

  const isTabular = useMemo(() => isTabularData(parsedResponse), [parsedResponse]);

  const serviceTypeLabel: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
    DB: { label: '数据库查询', color: '#722ed1', icon: <DatabaseOutlined /> },
    JSON: { label: '静态 JSON', color: '#13c2c2', icon: <CodeOutlined /> },
    STRING: { label: '静态文本', color: '#fa8c16', icon: <FileTextOutlined /> },
  };

  const typeInfo = serviceTypeLabel[serviceType || ''] || {
    label: serviceType || '未知', color: '#8c8c8c', icon: <ApiOutlined />,
  };

  const tabItems = [
    {
      key: 'result',
      label: (
        <span style={{ padding: '0 8px' }}>
          <DatabaseOutlined style={{ marginRight: 6 }} />
          执行结果
        </span>
      ),
      children: (
        <div className="log-detail-tab-pane">
          {parsedResponse == null ? (
            <Empty description="无返回数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : isTabular ? (
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
                      <LogResultTable data={parsedResponse} />
                    </div>
                  ),
                },
                {
                  key: 'raw',
                  label: '原始 JSON',
                  children: <LogCodePanel content={prettyJson(parsedResponse)} />,
                },
              ]}
            />
          ) : (
            <LogCodePanel content={prettyJson(parsedResponse)} />
          )}
        </div>
      ),
    },
    ...(sourceContent ? [{
      key: 'source',
      label: (
        <span style={{ padding: '0 8px' }}>
          <CodeOutlined style={{ marginRight: 6 }} />
          {sourceContent.type === 'SQL' ? 'SQL 语句' : sourceContent.type === 'JSON' ? 'JSON 内容' : '文本内容'}
        </span>
      ),
      children: (
        <div className="log-detail-tab-pane">
          <LogCodePanel
            content={
              sourceContent.language === 'json'
                ? prettyJson(sourceContent.content)
                : sourceContent.content
            }
            language={sourceContent.language}
          />
        </div>
      ),
    }] : []),
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
            <div className="log-detail-tab-pane">
              <Empty description="无请求参数" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            </div>
          );
        }
        return (
          <div className="log-detail-tab-pane">
            {businessParams ? (
              <>
                <div style={{ marginBottom: 8, fontSize: 13, fontWeight: 600, color: '#1f1f1f', flexShrink: 0 }}>
                  业务参数
                </div>
                <LogCodePanel content={prettyJson(businessParams)} />
              </>
            ) : (
              <div style={{ marginBottom: 16, color: '#8c8c8c', fontSize: 13 }}>无业务参数</div>
            )}
            {httpHeaders && (
              <Collapse
                ghost
                style={{ marginTop: 12, flexShrink: 0 }}
                items={[{
                  key: 'headers',
                  label: (
                    <span style={{ fontSize: 13, color: '#8c8c8c' }}>
                      HTTP 请求头（{Object.keys(httpHeaders).length} 项）
                    </span>
                  ),
                  children: <LogCodePanel content={prettyJson(httpHeaders)} height="220px" />,
                }]}
              />
            )}
          </div>
        );
      })(),
    },
    {
      key: 'trace',
      label: (
        <span style={{ padding: '0 8px' }}>
          <FileTextOutlined style={{ marginRight: 6 }} />
          原始 Trace
        </span>
      ),
      children: (
        <div className="log-detail-tab-pane">
          {traceData ? (
            <LogCodePanel content={prettyJson(safeParse(traceData))} />
          ) : (
            <Empty description="无 Trace 数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </div>
      ),
    },
  ];

  return (
    <LogDetailShell
      overview={{
        title: apiName || '未命名接口',
        icon: typeInfo.icon,
        iconColor: typeInfo.color,
        tags: (
          <>
            <Tag color={typeInfo.color} style={{ borderRadius: 4 }}>{typeInfo.label}</Tag>
            <Tag color="blue" style={{ fontFamily: 'monospace', borderRadius: 4 }}>{method}</Tag>
            <Text type="secondary" copyable style={{ fontSize: 13, fontFamily: 'monospace' }}>{url}</Text>
          </>
        ),
        success: status === 'SUCCESS',
        successText: '执行成功',
        failText: '执行失败',
        durationMs: costTimeMs,
        timeText: createTime,
      }}
      errorMessage={errorMsg}
      errorTitle="执行异常"
      tabItems={tabItems}
    />
  );
};

export default SimpleTraceViewer;
