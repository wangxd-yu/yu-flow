/**
 * MqTaskForm.tsx
 * MQ 任务管理 · 核心配置页面
 * - 草稿 + 发布快照；消费订阅仅跑已发布版本
 * - 支持草稿回滚到线上版本、模拟触发、调试运行、执行日志回放
 */
import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Drawer, message, Button, Form, Input, InputNumber, Switch, Space, Tooltip, Tag, Modal,
  Popconfirm, Select, AutoComplete, Radio,
} from 'antd';
import {
  SaveOutlined, CloseOutlined, PlayCircleOutlined, SendOutlined,
  CloudUploadOutlined, CloudDownloadOutlined, RollbackOutlined, DeleteOutlined,
} from '@ant-design/icons';
import { PageContainer, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import type { FlowMqTask, FlowMqTaskLog } from '@/services/flow/mqTask';
import FlowEditor from '@/components/flow/FlowEditorLazy';
import {
  debugRunMqTask,
  updateMqTask,
  publishMqTask,
  unpublishMqTask,
  republishMqTask,
  rollbackMqTask,
  getMqTask,
  simulateMqTask,
  queryMqTaskLogPage,
  getMqTaskLog,
  clearMqTaskLog,
} from '@/services/flow/mqTask';
import { queryMqConnectionOptions, queryMqTopics } from '@/services/flow/mqConnection';
import AssetRuntimePanel from '@/components/flow/AssetRuntimePanel';
import {
  AssetFormShell,
  ASSET_FORM_SHELL_CLASS,
  ASSET_FORM_FILL_CLASS,
} from '@/components/flow/ops';
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect';
import { useGlobalLogMode, getLogModeLabel } from '@/components/flow/useGlobalLogMode';

const DEFAULT_MQ_DSL = JSON.stringify({
  nodes: [
    {
      id: 'mq_trigger_1',
      type: 'mqTrigger',
      label: 'MQ Trigger',
      x: 80,
      y: 160,
      width: 240,
      height: 132,
      ports: [{ id: 'out' }],
      data: {
        __label: 'MQ Trigger',
      },
    },
  ],
  edges: [],
});

function unwrapTask(res: any): FlowMqTask {
  return (res?.data ?? res) as FlowMqTask;
}

function unwrapDebugTrace(result: any) {
  if (result?.code === 0 && result.data) return result.data;
  if (result?.data) return result.data;
  if (result?.traceId || result?.stepLogs) return result;
  throw new Error(result?.msg || '调试运行失败');
}

// ── 执行日志面板（编辑态 Tab） ──

const MqTaskLogPanel: React.FC<{
  taskId: string;
  taskName?: string;
  dslContent?: string;
}> = ({ taskId, taskName, dslContent }) => {
  const actionRef = useRef<ActionType>();
  const [traceOpen, setTraceOpen] = useState(false);
  const [trace, setTrace] = useState<any>(null);

  const openTrace = async (record: FlowMqTaskLog) => {
    const hide = message.loading('正在加载 Trace...');
    try {
      const res: any = await getMqTaskLog(record.id);
      const detail = res?.data ?? res;
      hide();
      if (!detail?.traceData) {
        message.info('该日志未记录 Trace 快照（任务未开启日志或执行未产生 Trace）');
        return;
      }
      setTrace(JSON.parse(detail.traceData));
      setTraceOpen(true);
    } catch (e: any) {
      hide();
      message.error(e?.message || '加载 Trace 失败');
    }
  };

  const columns: ProColumns<FlowMqTaskLog>[] = [
    {
      title: '触发类型',
      dataIndex: 'triggerType',
      width: 90,
      render: (_, r) =>
        r.triggerType === 'MANUAL' ? <Tag>手动</Tag> : <Tag color="blue">消息</Tag>,
    },
    { title: 'Topic', dataIndex: 'topic', width: 160, ellipsis: true },
    { title: '消息ID', dataIndex: 'messageId', width: 180, ellipsis: true, copyable: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_, r) =>
        r.status === 'SUCCESS' ? (
          <Tag color="success">成功</Tag>
        ) : r.status === 'RUNNING' ? (
          <Tag color="processing">运行中</Tag>
        ) : (
          <Tooltip title={r.errorMsg}>
            <Tag color="error">失败</Tag>
          </Tooltip>
        ),
    },
    {
      title: '耗时',
      dataIndex: 'costTimeMs',
      width: 90,
      render: (_, r) => (r.costTimeMs != null ? `${r.costTimeMs} ms` : '-'),
    },
    { title: '执行时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      valueType: 'option',
      width: 110,
      render: (_, r) => [
        r.hasTrace ? (
          <a key="trace" onClick={() => openTrace(r)}>
            查看 Trace
          </a>
        ) : (
          <span key="trace" style={{ color: 'rgba(0,0,0,0.25)' }}>
            无 Trace
          </span>
        ),
      ],
    },
  ];

  return (
    <div style={{ height: '100%', overflow: 'auto', padding: '0 8px' }}>
      <ProTable<FlowMqTaskLog>
        headerTitle="执行日志"
        actionRef={actionRef}
        rowKey="id"
        search={false}
        options={{ reload: true, density: false, setting: false }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        toolBarRender={() => [
          <Popconfirm
            key="clear"
            title="确定清空该任务的全部执行日志？"
            onConfirm={async () => {
              try {
                await clearMqTaskLog(taskId);
                message.success('已清空日志');
                actionRef.current?.reload();
              } catch (e: any) {
                message.error(e?.message || '清空失败');
              }
            }}
          >
            <Button danger icon={<DeleteOutlined />}>
              清空日志
            </Button>
          </Popconfirm>,
        ]}
        request={async (params = {}) => {
          const { current, pageSize } = params as any;
          const result = await queryMqTaskLogPage({
            taskId,
            page: (current || 1) - 1,
            size: pageSize || 20,
          });
          const data = (result as any)?.data || result;
          return { data: data?.items || [], success: true, total: data?.total || 0 };
        }}
        columns={columns}
      />

      {traceOpen && (
        <Drawer
          title={`执行 Trace - ${taskName || 'MQ 任务'}`}
          width="100%"
          open={traceOpen}
          onClose={() => {
            setTraceOpen(false);
            setTrace(null);
          }}
          styles={{ body: { padding: 0 } }}
          destroyOnClose
        >
          {trace ? (
            <FlowEditor
              value={dslContent}
              isEdit={false}
              readonlyTrace={trace}
              defaultEntryNode="mqTrigger"
              editorContext="mq"
            />
          ) : null}
        </Drawer>
      )}
    </div>
  );
};

// ── 主表单 ──

export interface MqTaskFormProps {
  visible: boolean;
  isEdit: boolean;
  initialValues?: Partial<FlowMqTask>;
  onCancel: () => void;
  onSubmit: (values: Partial<FlowMqTask>) => void;
  /** 发布 / 下线 / 回滚后通知列表刷新 */
  onPublished?: (detail: FlowMqTask) => void;
  /** 打开时默认 Tab（如运行中心深链） */
  initialTab?: string;
}

const MqTaskForm: React.FC<MqTaskFormProps> = ({
  visible, isEdit, initialValues = {}, onCancel, onSubmit, onPublished, initialTab,
}) => {
  const [form] = Form.useForm();
  const globalLogMode = useGlobalLogMode();

  const [name, setName] = useState<string>(initialValues.name || '');
  const [connectionCode, setConnectionCode] = useState<string>(initialValues.connectionCode || '');
  const [topic, setTopic] = useState<string>(initialValues.topic || '');
  const [consumerGroup, setConsumerGroup] = useState<string>(initialValues.consumerGroup || '');
  const [concurrency, setConcurrency] = useState<number>(initialValues.concurrency || 1);
  const [directoryId, setDirectoryId] = useState<string | undefined>(initialValues.directoryId);
  const [enabled, setEnabled] = useState<boolean>(initialValues.enabled !== false);
  const [logEnabled, setLogEnabled] = useState<boolean>(!!initialValues.logEnabled);
  const [logMode, setLogMode] = useState<string>(
    initialValues.logMode || (initialValues.logEnabled === false ? 'OFF' : (initialValues.logEnabled === true ? 'ALL' : 'SYSTEM_DEFAULT')),
  );
  const [logPayloadMode, setLogPayloadMode] = useState<string>(
    initialValues.logPayloadMode || 'SYSTEM_DEFAULT',
  );
  const [retryMax, setRetryMax] = useState<number>(initialValues.retryMax ?? 0);
  const [retryBackoffMs, setRetryBackoffMs] = useState<number>(initialValues.retryBackoffMs ?? 1000);
  const [deadLetterTopic, setDeadLetterTopic] = useState<string>(initialValues.deadLetterTopic || '');
  const [logRetentionDays, setLogRetentionDays] = useState<number | undefined>(
    initialValues.logRetentionDays ?? undefined,
  );
  const [info, setInfo] = useState<string>(initialValues.info || '');
  const [dslContent, setDslContent] = useState<string>(initialValues.dslContent || '');
  const [publishStatus, setPublishStatus] = useState<0 | 1>(
    initialValues.publishStatus === 1 ? 1 : 0,
  );
  const [hasUnpublishedChanges, setHasUnpublishedChanges] = useState<boolean>(
    !!initialValues.hasUnpublishedChanges,
  );
  const [activeTab, setActiveTab] = useState<string>('basic');
  const [submitAttempted, setSubmitAttempted] = useState<boolean>(false);
  const [debugReplayOpen, setDebugReplayOpen] = useState(false);
  const [debugReplayTrace, setDebugReplayTrace] = useState<any>(null);
  const [debugModalOpen, setDebugModalOpen] = useState(false);
  const [debugTopic, setDebugTopic] = useState('');
  const [debugBody, setDebugBody] = useState('{\n  "demo": true\n}');
  const [debugHeadersJson, setDebugHeadersJson] = useState('{}');
  const [connOptions, setConnOptions] = useState<{ label: string; value: string }[]>([]);
  const [topicOptions, setTopicOptions] = useState<{ value: string }[]>([]);
  const [topicLoading, setTopicLoading] = useState(false);
  const topicSearchTimer = useRef<any>(null);

  useEffect(() => {
    if (!visible) return;
    queryMqConnectionOptions()
      .then((res: any) => {
        const list = res?.data ?? res;
        setConnOptions(
          (list || []).map((c: any) => ({
            label: `${c.name} (${c.mqType})`,
            value: c.code,
          })),
        );
      })
      .catch(() => setConnOptions([]));
  }, [visible]);

  /**
   * 拉取 topic 候选：仅作输入提示，失败静默降级为手工输入。
   * Kafka 集群 topic 量可能上千，故走后端 keyword 过滤而非全量下拉。
   */
  const loadTopicOptions = useCallback((code: string, keyword?: string) => {
    if (!code) {
      setTopicOptions([]);
      return;
    }
    setTopicLoading(true);
    queryMqTopics(code, keyword, 50)
      .then((res: any) => {
        const list = res?.data ?? res;
        setTopicOptions(
          (Array.isArray(list) ? list : []).map((t: string) => ({ value: t })),
        );
      })
      .catch(() => setTopicOptions([]))
      .finally(() => setTopicLoading(false));
  }, []);

  // 切换连接后候选失效，重新按新连接拉取
  useEffect(() => {
    if (!visible) return;
    setTopicOptions([]);
    loadTopicOptions(connectionCode);
  }, [visible, connectionCode, loadTopicOptions]);

  // 输入时防抖远程搜索（组件卸载时清理定时器）
  const handleTopicSearch = useCallback(
    (keyword: string) => {
      if (topicSearchTimer.current) {
        clearTimeout(topicSearchTimer.current);
      }
      topicSearchTimer.current = setTimeout(
        () => loadTopicOptions(connectionCode, keyword),
        300,
      );
    },
    [connectionCode, loadTopicOptions],
  );

  useEffect(
    () => () => {
      if (topicSearchTimer.current) {
        clearTimeout(topicSearchTimer.current);
      }
    },
    [],
  );

  useEffect(() => {
    if (visible) {
      setName(initialValues.name || '');
      setConnectionCode(initialValues.connectionCode || '');
      setTopic(initialValues.topic || '');
      setConsumerGroup(initialValues.consumerGroup || '');
      setConcurrency(initialValues.concurrency || 1);
      setDirectoryId(initialValues.directoryId);
      setEnabled(initialValues.enabled !== false);
      setLogEnabled(!!initialValues.logEnabled);
      setLogMode(
        initialValues.logMode || (initialValues.logEnabled === false ? 'OFF' : (initialValues.logEnabled === true ? 'ALL' : 'SYSTEM_DEFAULT')),
      );
      setLogPayloadMode(initialValues.logPayloadMode || 'SYSTEM_DEFAULT');
      setRetryMax(initialValues.retryMax ?? 0);
      setRetryBackoffMs(initialValues.retryBackoffMs ?? 1000);
      setDeadLetterTopic(initialValues.deadLetterTopic || '');
      setLogRetentionDays(initialValues.logRetentionDays ?? undefined);
      setInfo(initialValues.info || '');
      setDslContent(initialValues.dslContent || '');
      setPublishStatus(initialValues.publishStatus === 1 ? 1 : 0);
      setHasUnpublishedChanges(!!initialValues.hasUnpublishedChanges);
      setSubmitAttempted(false);
      setActiveTab(
        initialTab && (['runtime', 'logs'].includes(initialTab) ? !!initialValues.id : true)
          ? initialTab
          : 'basic',
      );
    }
  }, [visible, initialValues, initialTab]);

  const buildPayload = useCallback((): Partial<FlowMqTask> | null => {
    setSubmitAttempted(true);
    if (!name?.trim()) {
      message.warning('请输入任务名称');
      return null;
    }
    if (!connectionCode) {
      message.warning('请选择 MQ 连接');
      return null;
    }
    if (!topic?.trim()) {
      message.warning('请输入订阅 Topic');
      return null;
    }
    return {
      name: name.trim(),
      connectionCode,
      topic: topic.trim(),
      consumerGroup: consumerGroup?.trim() || undefined,
      concurrency: concurrency || 1,
      enabled,
      logEnabled: logMode === 'ALL' || logMode === 'ERROR_ONLY',
      logMode,
      logPayloadMode,
      retryMax: retryMax ?? 0,
      retryBackoffMs: retryBackoffMs ?? 1000,
      deadLetterTopic: deadLetterTopic?.trim() || undefined,
      // 留空提交 -1：后端语义为清除任务级配置（回退系统保留天数）
      logRetentionDays: logRetentionDays ?? -1,
      info: info || undefined,
      dslContent: dslContent?.trim() || DEFAULT_MQ_DSL,
      directoryId: directoryId || '',
    };
  }, [name, connectionCode, topic, consumerGroup, concurrency, enabled, logMode, logPayloadMode, retryMax, retryBackoffMs, deadLetterTopic, logRetentionDays, info, dslContent, directoryId]);

  const handleSave = useCallback(async () => {
    const payload = buildPayload();
    if (!payload) return;
    onSubmit(payload);
  }, [buildPayload, onSubmit]);

  const applyDetail = useCallback((detail: FlowMqTask) => {
    setPublishStatus(detail.publishStatus === 1 ? 1 : 0);
    setHasUnpublishedChanges(!!detail.hasUnpublishedChanges);
    if (detail.dslContent != null) setDslContent(detail.dslContent);
    if (detail.name != null) setName(detail.name);
    if (detail.connectionCode != null) setConnectionCode(detail.connectionCode);
    if (detail.topic != null) setTopic(detail.topic);
    if (detail.consumerGroup !== undefined) setConsumerGroup(detail.consumerGroup || '');
    if (detail.concurrency != null) setConcurrency(detail.concurrency);
    if (detail.directoryId !== undefined) setDirectoryId(detail.directoryId || undefined);
    if (detail.enabled != null) setEnabled(!!detail.enabled);
    if (detail.logEnabled != null) setLogEnabled(!!detail.logEnabled);
    if (detail.logMode != null) setLogMode(detail.logMode);
    if (detail.logPayloadMode != null) setLogPayloadMode(detail.logPayloadMode);
    if (detail.retryMax != null) setRetryMax(detail.retryMax);
    if (detail.retryBackoffMs != null) setRetryBackoffMs(detail.retryBackoffMs);
    if (detail.deadLetterTopic !== undefined) setDeadLetterTopic(detail.deadLetterTopic || '');
    if (detail.logRetentionDays !== undefined) setLogRetentionDays(detail.logRetentionDays ?? undefined);
    if (detail.info != null) setInfo(detail.info);
    onPublished?.(detail);
  }, [onPublished]);

  const handlePublish = useCallback(async () => {
    if (!isEdit || !initialValues.id) {
      message.warning('请先保存任务后再发布');
      return;
    }
    const payload = buildPayload();
    if (!payload) return;

    const isRepublish = publishStatus === 1;
    const hide = message.loading(isRepublish ? '正在发布更新...' : '正在发布...');
    try {
      await updateMqTask(initialValues.id, payload);
      if (isRepublish) {
        await republishMqTask(initialValues.id);
      } else {
        await publishMqTask(initialValues.id);
      }
      const detail = unwrapTask(await getMqTask(initialValues.id));
      hide();
      message.success(isRepublish ? '发布更新成功' : '发布成功，消费订阅已生效');
      applyDetail(detail);
    } catch (e: any) {
      hide();
      message.error(e?.message || '发布失败');
    }
  }, [isEdit, initialValues.id, buildPayload, publishStatus, applyDetail]);

  const handleUnpublish = useCallback(async () => {
    if (!initialValues.id) return;
    const hide = message.loading('正在下线...');
    try {
      await unpublishMqTask(initialValues.id);
      const detail = unwrapTask(await getMqTask(initialValues.id));
      hide();
      message.success('下线成功，消费订阅已停止');
      applyDetail(detail);
    } catch (e: any) {
      hide();
      message.error(e?.message || '下线失败');
    }
  }, [initialValues.id, applyDetail]);

  const handleRollback = useCallback(async () => {
    if (!initialValues.id) return;
    const hide = message.loading('正在回滚...');
    try {
      await rollbackMqTask(initialValues.id);
      const detail = unwrapTask(await getMqTask(initialValues.id));
      hide();
      message.success('已回滚到线上版本');
      applyDetail(detail);
    } catch (e: any) {
      hide();
      message.error(e?.message || '回滚失败');
    }
  }, [initialValues.id, applyDetail]);

  const openDebugModal = useCallback(() => {
    if (!dslContent) {
      message.warning('请先配置流程');
      return;
    }
    setDebugTopic(topic || initialValues.topic || '');
    setDebugModalOpen(true);
  }, [dslContent, topic, initialValues.topic]);

  const executeDebugRun = useCallback(async () => {
    let parsedHeaders: Record<string, string> | undefined;
    if (debugHeadersJson.trim()) {
      try {
        parsedHeaders = JSON.parse(debugHeadersJson);
      } catch {
        message.error('消息头 JSON 格式不合法');
        return;
      }
    }
    const hide = message.loading('正在调试运行...');
    try {
      const result = await debugRunMqTask(dslContent, {
        sourceRef: initialValues.id,
        sourceName: name || initialValues.name,
        topic: debugTopic || topic || undefined,
        body: debugBody,
        headers: parsedHeaders,
      });
      const trace = unwrapDebugTrace(result);
      hide();
      setDebugModalOpen(false);
      setDebugReplayTrace(trace);
      setDebugReplayOpen(true);
      if (trace?.status === 'error') {
        message.error(`执行失败: ${trace.errorMsg || '见 Trace 详情'}`);
      } else {
        message.success('调试运行成功，已打开 Trace');
      }
    } catch (e: any) {
      hide();
      message.error('调试失败: ' + (e?.message || '未知错误'));
    }
  }, [dslContent, initialValues.id, initialValues.name, name, topic, debugTopic, debugBody, debugHeadersJson]);

  const handleSimulate = useCallback(() => {
    if (!initialValues.id || publishStatus !== 1) {
      message.warning('仅已发布任务可模拟触发');
      return;
    }
    let simMessage = '{\n  "demo": true\n}';
    Modal.confirm({
      title: '模拟触发一条消息',
      width: 520,
      content: (
        <div style={{ marginTop: 12 }}>
          <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 8 }}>
            按已发布版本异步执行一次，结果在「执行日志」查看（触发类型 = 手动）。
          </div>
          <Input.TextArea
            rows={6}
            defaultValue={simMessage}
            placeholder="模拟消息体（JSON 或纯文本，原样作为 $.mq.message 注入）"
            style={{ fontFamily: 'monospace', fontSize: 13 }}
            onChange={(e) => {
              simMessage = e.target.value;
            }}
          />
        </div>
      ),
      okText: '触发',
      onOk: async () => {
        try {
          await simulateMqTask(initialValues.id!, simMessage);
          message.success('已触发执行，请稍后在执行日志查看');
        } catch (e: any) {
          message.error(e?.message || '触发失败');
          throw e;
        }
      },
    });
  }, [initialValues.id, publishStatus]);

  const headerTitle = (
    <Space>
      <span style={{ fontWeight: 600, fontSize: 15 }}>
        {isEdit ? `编辑 MQ 任务：${name}` : '新建 MQ 任务'}
      </span>
      <Tag
        color={publishStatus === 1 ? 'success' : 'default'}
        style={{ padding: '2px 10px', fontSize: 12 }}
      >
        {publishStatus === 1 ? '● 已发布' : '○ 未发布'}
      </Tag>
      {isEdit && publishStatus === 1 && hasUnpublishedChanges && (
        <Tag color="warning">待更新发布</Tag>
      )}
    </Space>
  );

  const headerExtra = (
    <Space size={8}>
      <Tooltip title="调试：自定义消息体/Headers/Topic运行当前草稿流程，查看完整 Trace 轨迹">
        <Button icon={<PlayCircleOutlined />} onClick={openDebugModal}>
          调试运行
        </Button>
      </Tooltip>

      {isEdit && publishStatus === 1 && (
        <Tooltip title="按已发布版本模拟一条消息触发（异步，结果在执行日志）">
          <Button icon={<SendOutlined />} onClick={handleSimulate}>
            模拟触发
          </Button>
        </Tooltip>
      )}

      {isEdit && publishStatus === 1 && hasUnpublishedChanges && (
        <Tooltip title="将草稿回滚到已发布的线上版本">
          <Button danger icon={<RollbackOutlined />} onClick={handleRollback}>
            回滚草稿
          </Button>
        </Tooltip>
      )}

      {isEdit && (
        <Button
          type="primary"
          style={{ backgroundColor: publishStatus === 1 ? '#faad14' : '#52c41a' }}
          icon={<CloudUploadOutlined />}
          onClick={handlePublish}
        >
          {publishStatus === 1 ? '保存并发布' : '发布上线'}
        </Button>
      )}

      {isEdit && publishStatus === 1 && (
        <Button danger icon={<CloudDownloadOutlined />} onClick={handleUnpublish}>
          下线
        </Button>
      )}

      <Button icon={<CloseOutlined />} onClick={onCancel}>取消</Button>
      <Button type="primary" icon={<SaveOutlined />} onClick={handleSave}>
        保存草稿
      </Button>
    </Space>
  );

  const basicInfoContent = (
    <div style={{ maxWidth: 600, padding: '16px 0' }}>
      <Form layout="vertical" form={form}>
        <Form.Item
          label="任务名称"
          required
          validateStatus={submitAttempted && !name?.trim() ? 'error' : ''}
          help={submitAttempted && !name?.trim() ? '请输入任务名称' : undefined}
        >
          <Input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="请输入 MQ 任务名称"
          />
        </Form.Item>

        <DirectoryTreeSelect
          bizType="mqtask"
          name="directoryId"
          label="所属目录"
          placeholder="不选默认为根目录"
          fieldProps={{
            showSearch: true,
            treeDefaultExpandAll: true,
            allowClear: true,
            value: directoryId,
            onChange: (v: string | undefined) => setDirectoryId(v || undefined),
            style: { width: '100%' },
          }}
        />

        <Form.Item
          label="MQ 连接"
          required
          validateStatus={submitAttempted && !connectionCode ? 'error' : ''}
          help={
            submitAttempted && !connectionCode
              ? '请选择 MQ 连接'
              : (
                <span style={{ fontSize: 12, color: '#8c8c8c' }}>
                  连接在「消息队列 → 连接配置」中维护，需已启用。
                </span>
              )
          }
        >
          <Select
            value={connectionCode || undefined}
            placeholder="选择 MQ 连接..."
            options={connOptions}
            showSearch
            optionFilterProp="label"
            allowClear
            onChange={(v) => setConnectionCode(v || '')}
          />
        </Form.Item>

        <Form.Item
          label="订阅 Topic / 队列"
          required
          validateStatus={submitAttempted && !topic?.trim() ? 'error' : ''}
          help={
            submitAttempted && !topic?.trim()
              ? '请输入订阅 Topic'
              : (
                <span style={{ fontSize: 12, color: '#8c8c8c' }}>
                  RabbitMQ 填队列名；Kafka 可从候选中选择或直接输入。发布后开始消费。
                </span>
              )
          }
        >
          <AutoComplete
            value={topic}
            options={topicOptions}
            onChange={(v) => setTopic(v || '')}
            onSearch={handleTopicSearch}
            filterOption={false}
            notFoundContent={topicLoading ? '加载中...' : null}
            placeholder="例如：order.created"
            style={{ width: '100%', fontFamily: 'monospace' }}
          />
        </Form.Item>

        <Form.Item
          label="消费组"
          help={
            <span style={{ fontSize: 12, color: '#8c8c8c' }}>
              Kafka group.id（默认 yu-flow-{'{taskId}'}）；RabbitMQ 忽略该项。
            </span>
          }
        >
          <Input
            value={consumerGroup}
            onChange={(e) => setConsumerGroup(e.target.value)}
            placeholder="可选"
            style={{ fontFamily: 'monospace' }}
          />
        </Form.Item>

        <Form.Item label="消费并发数">
          <InputNumber
            min={1}
            max={32}
            value={concurrency}
            onChange={(v) => setConcurrency(v || 1)}
          />
        </Form.Item>

        <Form.Item label="启用状态">
          <Switch
            checked={enabled}
            onChange={setEnabled}
            checkedChildren="启用"
            unCheckedChildren="停用"
          />
          <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
            启用且已发布后才会订阅消费；调试运行始终使用当前草稿。
          </div>
        </Form.Item>

        <Form.Item label="日志策略">
          <Radio.Group
            value={logMode}
            onChange={(e) => setLogMode(e.target.value)}
            optionType="button"
            buttonStyle="solid"
          >
            <Tooltip title={`跟随系统全局配置（当前全局：${getLogModeLabel(globalLogMode)}，可在「系统配置」中热更）`}>
              <Radio.Button value="SYSTEM_DEFAULT">继承全局</Radio.Button>
            </Tooltip>
            <Tooltip title="显式指定当前 MQ 消费仅在发生报错/失败时记录日志与原始报文">
              <Radio.Button value="ERROR_ONLY">仅错误</Radio.Button>
            </Tooltip>
            <Tooltip title="显式指定当前 MQ 消费全量记录成功与失败日志（含 FlowTrace 快照及原始报文）">
              <Radio.Button value="ALL">全量记录</Radio.Button>
            </Tooltip>
            <Tooltip title="显式指定当前 MQ 消费完全禁用日志记录，任何情况下均不落库">
              <Radio.Button value="OFF">完全关闭</Radio.Button>
            </Tooltip>
          </Radio.Group>
          <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
            {(!logMode || logMode === 'SYSTEM_DEFAULT') && `继承全局策略：当前全局生效为【${getLogModeLabel(globalLogMode)}】（来自系统配置 ENGINE_DEFAULT_LOG_MODE）`}
            {logMode === 'ERROR_ONLY' && '覆盖全局配置：显式指定当前任务为【仅错误】，平时零开销，异常时保存错误与原始报文'}
            {logMode === 'ALL' && '覆盖全局配置：显式指定当前任务为【全量记录】，全量保存消息日志、FlowTrace 快照及原始报文'}
            {logMode === 'OFF' && '覆盖全局配置：显式指定当前任务为【完全关闭】，任何情况下均不保存日志'}
          </div>
        </Form.Item>

        <Form.Item label="报文留存策略">
          <Radio.Group
            value={logPayloadMode}
            onChange={(e) => setLogPayloadMode(e.target.value)}
            optionType="button"
            buttonStyle="solid"
          >
            <Tooltip title="跟随系统全局 MQ_LOG_PAYLOAD_MODE（默认 FULL）">
              <Radio.Button value="SYSTEM_DEFAULT">继承全局</Radio.Button>
            </Tooltip>
            <Tooltip title="落库完整报文（受 log-body-max-chars 截断）">
              <Radio.Button value="FULL">明文</Radio.Button>
            </Tooltip>
            <Tooltip title="仅存占位符 [MASKED len=N]，不存明文">
              <Radio.Button value="MASK">脱敏</Radio.Button>
            </Tooltip>
            <Tooltip title="不存储 messageBody / messageHeaders">
              <Radio.Button value="OFF">不存</Radio.Button>
            </Tooltip>
          </Radio.Group>
        </Form.Item>

        <Form.Item label="失败重试">
          <Space>
            <span>最多</span>
            <InputNumber min={0} max={10} value={retryMax} onChange={(v) => setRetryMax(v ?? 0)} />
            <span>次，间隔</span>
            <InputNumber min={0} max={60000} step={500} value={retryBackoffMs} onChange={(v) => setRetryBackoffMs(v ?? 1000)} />
            <span>ms</span>
          </Space>
          <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
            应用层重试，不影响 MQ ack；仅最终 outcome 记一条日志。
          </div>
        </Form.Item>

        <Form.Item
          label="死信 Topic"
          help={
            <span style={{ fontSize: 12, color: '#8c8c8c' }}>
              最终仍失败时，将原文转发到此 topic/队列（同连接）；留空则不转发。
            </span>
          }
        >
          <Input
            value={deadLetterTopic}
            onChange={(e) => setDeadLetterTopic(e.target.value)}
            placeholder="例如：order.created.dlq"
            style={{ fontFamily: 'monospace' }}
          />
        </Form.Item>

        <Form.Item label="日志保留天数">
          <InputNumber
            value={logRetentionDays}
            onChange={(v) => setLogRetentionDays(v ?? undefined)}
            min={0}
            precision={0}
            style={{ width: 200 }}
            placeholder="留空跟随系统配置"
          />
          <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
            留空=跟随系统配置（LOG_MQ_TASK_RETENTION_DAYS），0=永久保留，&gt;0=按天数自动清理
          </div>
        </Form.Item>

        <Form.Item label="任务描述">
          <Input.TextArea
            value={info}
            onChange={(e) => setInfo(e.target.value)}
            rows={3}
            placeholder="可选：任务的功能说明"
          />
        </Form.Item>
      </Form>
    </div>
  );

  const renderTabContent = () => {
    if (activeTab === 'basic') {
      return basicInfoContent;
    }
    if (activeTab === 'runtime') {
      return (
        <div style={{ overflow: 'auto', height: '100%', padding: '0 8px' }}>
          <AssetRuntimePanel assetType="MQ_TASK" assetId={initialValues.id} />
        </div>
      );
    }
    if (activeTab === 'logs') {
      return (
        <MqTaskLogPanel
          taskId={initialValues.id!}
          taskName={name || initialValues.name}
          dslContent={dslContent}
        />
      );
    }
    return (
      <div className={ASSET_FORM_FILL_CLASS}>
        <FlowEditor
          value={dslContent}
          onChange={setDslContent}
          onSave={handleSave}
          onCancel={onCancel}
          isEdit={isEdit}
          height={400}
          defaultEntryNode="mqTrigger"
          editorContext="mq"
          apiId={initialValues.id}
          apiName={name}
          defaultTriggerBody='{"demo":true}'
          debugAdapters={{
            onRun: async (payload) => {
              const result = await debugRunMqTask(payload.dslContent, {
                sourceRef: initialValues.id,
                sourceName: name,
                topic: payload.queryParams?.topic || topic || undefined,
                body: payload.body,
                headers: payload.headers,
              });
              return unwrapDebugTrace(result);
            },
          }}
        />
      </div>
    );
  };

  return (
    <AssetFormShell open={visible} onClose={onCancel}>
      <PageContainer
        className={ASSET_FORM_SHELL_CLASS}
        header={{
          title: headerTitle,
          extra: headerExtra,
          style: { paddingBottom: 0 },
          breadcrumb: {},
        }}
        tabActiveKey={activeTab}
        onTabChange={setActiveTab}
        tabList={[
          { tab: '基本信息', key: 'basic' },
          { tab: '流程编排', key: 'flow' },
          ...(initialValues.id
            ? [
                { tab: '运行', key: 'runtime' },
                { tab: '执行日志', key: 'logs' },
              ]
            : []),
        ]}
        style={{ height: '100%', overflow: 'hidden' }}
      >
        {renderTabContent()}
      </PageContainer>

      <Modal
        title="MQ 调试运行 - 触发器参数配置"
        open={debugModalOpen}
        onCancel={() => setDebugModalOpen(false)}
        onOk={executeDebugRun}
        okText="开始调试"
        width={560}
        destroyOnClose
      >
        <Form layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="模拟 Topic" help="MQ 消息来源 Topic（写入 $.mq.topic）">
            <Input
              value={debugTopic}
              onChange={(e) => setDebugTopic(e.target.value)}
              placeholder="请输入 Topic，例如 demo-topic"
            />
          </Form.Item>
          <Form.Item label="模拟消息体 (Body)" help="消息内容，JSON 格式会自动解析为对象注入 $.mq.message">
            <Input.TextArea
              rows={6}
              value={debugBody}
              onChange={(e) => setDebugBody(e.target.value)}
              placeholder='例如: {"orderId": "1001", "amount": 99.9}'
              style={{ fontFamily: 'monospace', fontSize: 13 }}
            />
          </Form.Item>
          <Form.Item label="消息头 (Headers，可选)" help="消息头 KV 字典（JSON 格式），注入 $.mq.headers">
            <Input.TextArea
              rows={3}
              value={debugHeadersJson}
              onChange={(e) => setDebugHeadersJson(e.target.value)}
              placeholder='例如: {"correlationId": "req-123", "contentType": "application/json"}'
              style={{ fontFamily: 'monospace', fontSize: 13 }}
            />
          </Form.Item>
        </Form>
      </Modal>

      {debugReplayOpen && (
        <Drawer
          title={`调试 Trace - ${name || initialValues.name || 'MQ 任务'}`}
          width="100%"
          open={debugReplayOpen}
          onClose={() => {
            setDebugReplayOpen(false);
            setDebugReplayTrace(null);
          }}
          styles={{ body: { padding: 0 } }}
          destroyOnClose
        >
          {debugReplayTrace ? (
            <FlowEditor
              value={dslContent}
              isEdit={false}
              readonlyTrace={debugReplayTrace}
              defaultEntryNode="mqTrigger"
              editorContext="mq"
            />
          ) : null}
        </Drawer>
      )}
    </AssetFormShell>
  );
};

export default MqTaskForm;
