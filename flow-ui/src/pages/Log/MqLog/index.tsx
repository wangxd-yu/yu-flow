import React, { useRef, useState } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { Alert, Button, Drawer, Input, Modal, message, Popconfirm, Tabs, Tag, Tooltip, Typography } from 'antd';
import { EyeOutlined, FileTextOutlined, PlayCircleOutlined } from '@ant-design/icons';
import FlowEditor from '@/components/flow/FlowEditorLazy';
import type { FlowTrace } from '@/components/flow/debugger/FlowDebugger';
import {
  queryMqTaskLogPage,
  getMqTaskLog,
  getMqTask,
  clearMqTaskLog,
  simulateMqTask,
  FlowMqTaskLog,
} from '@/services/flow/mqTask';
import { LogStatusTag, LogDuration, type LogStatusKind, LogDeepLinkBar, LogCodePanel } from '../shared';
import '@/styles/fullHeightTable.css';
import '../shared/logPageLayout.css';

const { Text } = Typography;

const STATUS_TAG: Record<string, { kind: LogStatusKind; text: string }> = {
  SUCCESS: { kind: 'success', text: '消费成功' },
  FAILED: { kind: 'error', text: '消费失败' },
  RUNNING: { kind: 'processing', text: '消费中' },
  SKIPPED: { kind: 'skipped', text: '已跳过' },
};

/** 列表扫障：按 status + errorMsg 归类失败原因（零 DDL） */
function classifyFailReason(record: FlowMqTaskLog): { text: string; color: string } | null {
  if (record.status === 'SUCCESS' || record.status === 'RUNNING') return null;
  if (record.status === 'SKIPPED') {
    return { text: '幂等跳过', color: 'default' };
  }
  const msg = record.errorMsg || '';
  if (msg.includes('消息体超过大小上限')) {
    return { text: '超限拒绝', color: 'orange' };
  }
  if (msg.includes('未发布') || msg.includes('发布快照为空')) {
    return { text: '未发布', color: 'gold' };
  }
  if (msg.includes('死信') || msg.includes('重试耗尽')) {
    return { text: '毒消息', color: 'magenta' };
  }
  if (record.status === 'FAILED') {
    return { text: '流程失败', color: 'error' };
  }
  return null;
}

function parseHeadersJson(raw?: string): Record<string, any> | undefined {
  if (!raw?.trim()) return undefined;
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}

const MqLogPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [currentLog, setCurrentLog] = useState<FlowMqTaskLog | null>(null);
  const [currentDsl, setCurrentDsl] = useState('');
  const [currentTrace, setCurrentTrace] = useState<FlowTrace | null>(null);

  const [rawDrawerVisible, setRawDrawerVisible] = useState(false);
  const [rawLogDetail, setRawLogDetail] = useState<FlowMqTaskLog | null>(null);
  const [rawActiveTab, setRawActiveTab] = useState<'body' | 'headers'>('body');

  const urlParams = new URLSearchParams(window.location.search);
  // 兼容运行中心 / 任务页两种深链参数命名
  const initialTaskId =
    urlParams.get('taskId') || urlParams.get('mqTaskId') || undefined;

  const handleViewTrace = async (record: FlowMqTaskLog) => {
    if (!record.hasTrace) {
      message.warning('该消费日志没有关联的追踪快照数据');
      return;
    }

    try {
      const res: any = await getMqTaskLog(record.id);
      const log: FlowMqTaskLog = res?.data ?? res;
      if (!log?.traceData) {
        message.warning('未找到该消费日志的追踪快照数据');
        return;
      }

      const trace: FlowTrace & { dslSnapshot?: string; dslContentHash?: string } =
        JSON.parse(log.traceData);
      let dslContent = trace.dslSnapshot || '';
      if (!dslContent && log.taskId) {
        try {
          const taskRes: any = await getMqTask(log.taskId);
          dslContent = (taskRes?.data ?? taskRes)?.dslContent || '';
        } catch (e) {
          console.error('getMqTask failed:', e);
        }
      }
      if (!dslContent) {
        message.warning('无法加载任务 DSL，画布将显示为空');
      }

      setCurrentLog(log);
      setCurrentTrace(trace);
      setCurrentDsl(dslContent);
      setDrawerVisible(true);
    } catch (e) {
      message.error('解析执行快照失败');
      console.error(e);
    }
  };

  const handleViewRawMessage = async (record: FlowMqTaskLog) => {
    try {
      const res: any = await getMqTaskLog(record.id);
      const log: FlowMqTaskLog = res?.data ?? res;
      setRawLogDetail(log);
      setRawActiveTab('body');
      setRawDrawerVisible(true);
    } catch (e) {
      message.error('获取原始报文失败');
    }
  };

  const handleSimulateFromLog = async (record: FlowMqTaskLog) => {
    if (!record.taskId) {
      message.warning('缺少任务 ID，无法模拟');
      return;
    }
    try {
      const [logRes, taskRes]: any[] = await Promise.all([
        getMqTaskLog(record.id),
        getMqTask(record.taskId),
      ]);
      const log: FlowMqTaskLog = logRes?.data ?? logRes;
      const task = taskRes?.data ?? taskRes;
      if (!task || task.publishStatus !== 1) {
        message.warning('任务未发布，无法模拟触发。请先发布，或在任务编辑页使用「调试运行」。');
        return;
      }
      if (!log?.messageBody) {
        message.warning('该条日志未留存原始报文，无法预填模拟');
        return;
      }
      let simMessage = log.messageBody;
      let simHeadersText = log.messageHeaders || '';
      Modal.confirm({
        title: `用此报文模拟 - ${task.name || record.taskName || ''}`,
        width: 640,
        content: (
          <div style={{ marginTop: 12 }}>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 12 }}
              message="将按已发布版本异步执行一次，结果写入 MQ 日志（触发类型=手动模拟）"
            />
            <div style={{ marginBottom: 8, color: 'rgba(0,0,0,0.65)' }}>消息体</div>
            <Input.TextArea
              rows={6}
              defaultValue={simMessage}
              onChange={(e) => {
                simMessage = e.target.value;
              }}
            />
            <div style={{ margin: '12px 0 8px', color: 'rgba(0,0,0,0.65)' }}>
              消息头 JSON（可选）
            </div>
            <Input.TextArea
              rows={3}
              defaultValue={simHeadersText}
              placeholder='例如 {"x-trace-id":"abc"}'
              onChange={(e) => {
                simHeadersText = e.target.value;
              }}
            />
          </div>
        ),
        okText: '触发模拟',
        onOk: async () => {
          let headers: Record<string, any> | undefined;
          if (simHeadersText.trim()) {
            headers = parseHeadersJson(simHeadersText);
            if (!headers) {
              message.error('消息头不是合法 JSON 对象');
              throw new Error('invalid headers');
            }
          } else {
            headers = parseHeadersJson(log.messageHeaders);
          }
          await simulateMqTask(record.taskId!, simMessage, headers);
          message.success('已触发模拟，请稍后刷新日志查看结果');
          actionRef.current?.reload();
        },
      });
    } catch (e: any) {
      if (e?.message !== 'invalid headers') {
        message.error(e?.message || '打开模拟失败');
      }
    }
  };

  const columns: ProColumns<FlowMqTaskLog>[] = [
    {
      title: '序号',
      valueType: 'index',
      width: 60,
      fixed: 'left',
      search: false,
    },
    {
      title: '任务名称',
      dataIndex: 'taskName',
      width: 160,
      fixed: 'left',
      ellipsis: true,
      render: (_, record) => (
        <Text strong style={{ color: '#1677ff' }}>
          {record.taskName || '未命名'}
        </Text>
      ),
    },
    {
      title: 'Topic',
      dataIndex: 'topic',
      width: 180,
      ellipsis: true,
      copyable: true,
    },
    {
      title: '消息ID',
      dataIndex: 'messageId',
      width: 200,
      ellipsis: true,
      copyable: true,
      render: (_, record) =>
        record.messageId ? (
          <Text style={{ fontFamily: 'monospace', fontSize: 12 }}>{record.messageId}</Text>
        ) : (
          '-'
        ),
    },
    {
      title: '触发类型',
      dataIndex: 'triggerType',
      width: 100,
      valueEnum: {
        MQ: { text: '消息触发' },
        MANUAL: { text: '手动模拟' },
      },
      render: (_, record) => (
        <Tag color={record.triggerType === 'MANUAL' ? 'blue' : 'purple'}>
          {record.triggerType === 'MANUAL' ? '手动模拟' : '消息触发'}
        </Tag>
      ),
    },
    {
      title: '消费状态',
      dataIndex: 'status',
      width: 120,
      valueEnum: {
        SUCCESS: { text: '成功', status: 'Success' },
        FAILED: { text: '失败', status: 'Error' },
        RUNNING: { text: '消费中', status: 'Processing' },
        SKIPPED: { text: '已跳过', status: 'Default' },
      },
      render: (_, record) => {
        const s = STATUS_TAG[record.status || ''];
        if (!s) {
          return record.status || '-';
        }
        const tag = <LogStatusTag kind={s.kind} text={s.text} />;
        return record.errorMsg ? <Tooltip title={record.errorMsg}>{tag}</Tooltip> : tag;
      },
    },
    {
      title: '失败原因',
      dataIndex: 'failReason',
      width: 110,
      search: false,
      render: (_, record) => {
        const reason = classifyFailReason(record);
        if (!reason) return <Text type="secondary">-</Text>;
        return (
          <Tooltip title={record.errorMsg || reason.text}>
            <Tag color={reason.color} style={{ margin: 0 }}>
              {reason.text}
            </Tag>
          </Tooltip>
        );
      },
    },
    {
      title: '错误摘要',
      dataIndex: 'errorMsg',
      width: 220,
      ellipsis: true,
      search: false,
      render: (_, record) =>
        record.errorMsg ? (
          <Tooltip title={record.errorMsg}>
            <Text type="danger" style={{ fontSize: 12 }}>
              {record.errorMsg}
            </Text>
          </Tooltip>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: '耗时',
      dataIndex: 'costTimeMs',
      width: 100,
      search: false,
      render: (_, record) => <LogDuration ms={record.costTimeMs} />,
    },
    {
      title: '消费时间',
      dataIndex: 'createTime',
      width: 180,
      valueType: 'dateTimeRange',
      fieldProps: {
        placeholder: ['开始时间', '结束时间'],
      },
      render: (_, record) => record.createTime || '-',
      search: {
        transform: (value) => ({
          createTime: value,
        }),
      },
    },
    {
      title: '操作',
      valueType: 'option',
      width: 260,
      fixed: 'right',
      render: (_, record) => [
        <Tooltip
          key="raw"
          title={
            record.hasMessageBody === false
              ? '该条日志未留存原始报文（日志策略关闭、消息体为空，或升级前 ERROR_ONLY 未落库）'
              : undefined
          }
        >
          <span>
            <Button
              type="link"
              size="small"
              icon={<FileTextOutlined />}
              onClick={() => handleViewRawMessage(record)}
              disabled={record.hasMessageBody === false}
            >
              原始报文
            </Button>
          </span>
        </Tooltip>,
        <Tooltip
          key="sim"
          title={
            record.hasMessageBody === false
              ? '无原始报文，无法预填模拟'
              : '用此报文按已发布版本模拟触发'
          }
        >
          <span>
            <Button
              type="link"
              size="small"
              icon={<PlayCircleOutlined />}
              onClick={() => handleSimulateFromLog(record)}
              disabled={record.hasMessageBody === false}
            >
              模拟
            </Button>
          </span>
        </Tooltip>,
        <Button
          key="view"
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => handleViewTrace(record)}
          disabled={!record.hasTrace}
        >
          查看快照
        </Button>,
      ],
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
      header={{
        title: 'MQ 日志',
        subTitle: '追溯消息队列消费的执行状态、消息来源、耗时及流程编排快照',
      }}
    >
      <ProTable<FlowMqTaskLog>
        className="fh-table"
        rowKey="id"
        actionRef={actionRef}
        headerTitle={
          initialTaskId ? (
            <LogDeepLinkBar label={`taskId=${initialTaskId}`} clearPath="/log/mq" />
          ) : (
            '全部 MQ 消费日志'
          )
        }
        tableLayout="fixed"
        scroll={{ x: 1680, y: 100000 }}
        search={{
          labelWidth: 'auto',
          defaultCollapsed: false,
        }}
        toolBarRender={() => [
          <Tooltip
            key="upgrade-hint"
            title="升级前 ERROR_ONLY 失败日志可能无原始报文；升级后新产生的失败/跳过记录会留存报文（受报文策略约束）"
          >
            <Tag color="processing" style={{ marginRight: 0 }}>
              升级说明
            </Tag>
          </Tooltip>,
          initialTaskId && (
            <Popconfirm
              key="clear"
              title="确定清空该任务的全部消费日志？"
              onConfirm={async () => {
                await clearMqTaskLog(initialTaskId);
                message.success('日志已清空');
                actionRef.current?.reload();
              }}
            >
              <Button danger>清空日志</Button>
            </Popconfirm>
          ),
        ]}
        params={{ taskId: initialTaskId }}
        request={async (params) => {
          const { createTime, ...rest } = params;
          let startTime: string | undefined;
          let endTime: string | undefined;
          if (createTime && Array.isArray(createTime)) {
            startTime = createTime[0];
            endTime = createTime[1];
          }

          const result = await queryMqTaskLogPage({
            taskId: rest.taskId || initialTaskId,
            taskName: rest.taskName,
            topic: rest.topic,
            messageId: rest.messageId,
            status: rest.status,
            triggerType: rest.triggerType,
            startTime,
            endTime,
            page: (rest.current || 1) - 1,
            size: rest.pageSize || 20,
          });
          const data = (result as any)?.data || result;
          return {
            data: data?.items || [],
            success: true,
            total: data?.total || 0,
          };
        }}
        columns={columns}
        rowClassName={(record) => (record.status === 'FAILED' ? 'log-row-fail' : '')}
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
        }}
        options={{
          density: true,
          fullScreen: true,
          reload: true,
          setting: true,
        }}
      />

      {rawDrawerVisible && (
        <Drawer
          title={`原始报文 - ${rawLogDetail?.taskName || 'MQ 消息'}`}
          width={640}
          open={rawDrawerVisible}
          onClose={() => setRawDrawerVisible(false)}
          destroyOnClose
          extra={
            rawLogDetail?.messageBody ? (
              <Button
                type="primary"
                size="small"
                icon={<PlayCircleOutlined />}
                onClick={() => {
                  setRawDrawerVisible(false);
                  handleSimulateFromLog(rawLogDetail);
                }}
              >
                用此报文模拟
              </Button>
            ) : null
          }
        >
          <div style={{ marginBottom: 16, display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
            <Tag color="cyan">Topic: {rawLogDetail?.topic || '-'}</Tag>
            <Tag color="blue">MessageID: {rawLogDetail?.messageId || '-'}</Tag>
            <Tag color={rawLogDetail?.status === 'SUCCESS' ? 'success' : rawLogDetail?.status === 'FAILED' ? 'error' : 'default'}>
              {rawLogDetail?.status}
            </Tag>
          </div>
          {rawLogDetail?.messageBody?.includes('...(truncated)') && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 12 }}
              message="报文已截断，非完整原文"
              description="超过 yu-flow.mq.log-body-max-chars 上限的部分已丢弃，对账请以消息中间件侧原文为准。"
            />
          )}
          <Tabs
            activeKey={rawActiveTab}
            onChange={(k) => setRawActiveTab(k as any)}
            items={[
              {
                key: 'body',
                label: '原始消息体 (Body)',
                children: (
                  <div style={{ height: 440 }}>
                    <LogCodePanel
                      content={rawLogDetail?.messageBody}
                      language={
                        rawLogDetail?.messageBody?.trim().startsWith('{') || rawLogDetail?.messageBody?.trim().startsWith('[')
                          ? 'json'
                          : 'text'
                      }
                      emptyText="未记录原始消息体（日志策略为「完全关闭」、消息体为空，或升级前 ERROR_ONLY 未落库）"
                      height="100%"
                    />
                  </div>
                ),
              },
              {
                key: 'headers',
                label: '消息头 (Headers)',
                children: (
                  <div style={{ height: 440 }}>
                    <LogCodePanel
                      content={rawLogDetail?.messageHeaders}
                      language="json"
                      emptyText="未记录消息头（无 Headers，或升级前未落库）"
                      height="100%"
                    />
                  </div>
                ),
              },
            ]}
          />
        </Drawer>
      )}

      {drawerVisible && (
        <Drawer
          title={`消费快照复原 - ${currentLog?.taskName || ''}`}
          width="100%"
          open={drawerVisible}
          onClose={() => setDrawerVisible(false)}
          styles={{ body: { padding: 0 } }}
          destroyOnClose
        >
          {currentTrace ? (
            <FlowEditor value={currentDsl} isEdit={false} readonlyTrace={currentTrace} />
          ) : null}
        </Drawer>
      )}
    </PageContainer>
  );
};

export default MqLogPage;
