/**
 * TaskForm.tsx
 * 任务管理 · 核心配置页面
 * - 草稿 + 发布快照；调度仅跑已发布版本
 * - 支持历史版本回退（同步草稿与线上快照）
 */
import React, { useState, useEffect, useCallback } from 'react';
import {
  Drawer, message, Button, Form, Input, InputNumber, Switch, Space, Tooltip, Tag, Radio, Row, Col,
} from 'antd';
import {
  SaveOutlined, CloseOutlined, PlayCircleOutlined,
  CloudUploadOutlined, CloudDownloadOutlined, RollbackOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import type { FlowTask } from '@/services/flow/taskService';
import FlowEditor from '@/components/flow/FlowEditorLazy';
import {
  debugRunTask,
  updateTask,
  publishTask,
  unpublishTask,
  republishTask,
  rollbackTask,
  getTask,
  listTaskVersions,
  restoreTaskVersion,
} from '@/services/flow/taskService';
import AssetVersionHistoryDrawer, { HistoryVersionButton } from '@/components/flow/AssetVersionHistoryDrawer';
import AssetRuntimePanel from '@/components/flow/AssetRuntimePanel';
import {
  AssetFormShell,
  ASSET_FORM_SHELL_CLASS,
  ASSET_FORM_FILL_CLASS,
  ASSET_FORM_SCROLL_CLASS,
  ASSET_FORM_BASIC_CLASS,
  ASSET_FORM_COL_FIELD,
  ASSET_FORM_COL_HALF,
  ASSET_FORM_COL_FULL,
} from '@/components/flow/ops';
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect';
import { useGlobalLogMode, getLogModeLabel } from '@/components/flow/useGlobalLogMode';

const DEFAULT_SCHEDULE_DSL = JSON.stringify({
  nodes: [
    {
      id: 'schedule_1',
      type: 'schedule',
      label: 'Schedule',
      x: 80,
      y: 160,
      width: 240,
      height: 104,
      ports: [{ id: 'out' }],
      data: {
        __label: 'Schedule',
      },
    },
  ],
  edges: [],
});

function unwrapTask(res: any): FlowTask {
  return (res?.data ?? res) as FlowTask;
}

function unwrapDebugTrace(result: any) {
  if (result?.code === 0 && result.data) return result.data;
  if (result?.data) return result.data;
  if (result?.traceId || result?.stepLogs) return result;
  throw new Error(result?.msg || '调试运行失败');
}

export interface TaskFormProps {
  visible: boolean;
  isEdit: boolean;
  initialValues?: Partial<FlowTask>;
  onCancel: () => void;
  onSubmit: (values: Partial<FlowTask>) => void;
  /** 发布 / 下线 / 回滚 / 版本恢复后通知列表刷新 */
  onPublished?: (detail: FlowTask) => void;
  /** 打开时默认 Tab（如运行中心深链） */
  initialTab?: string;
}

const TaskForm: React.FC<TaskFormProps> = ({
  visible, isEdit, initialValues = {}, onCancel, onSubmit, onPublished, initialTab,
}) => {
  const [form] = Form.useForm();
  const globalLogMode = useGlobalLogMode();

  const [name, setName] = useState<string>(initialValues.name || '');
  const [cron, setCron] = useState<string>(initialValues.cron || '');
  const [directoryId, setDirectoryId] = useState<string | undefined>(initialValues.directoryId);
  const [enabled, setEnabled] = useState<boolean>(initialValues.enabled !== false);
  const [logEnabled, setLogEnabled] = useState<boolean>(!!initialValues.logEnabled);
  const [logMode, setLogMode] = useState<string>(
    initialValues.logMode || (initialValues.logEnabled === false ? 'OFF' : (initialValues.logEnabled === true ? 'ALL' : 'SYSTEM_DEFAULT')),
  );
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
  const [historyOpen, setHistoryOpen] = useState(false);
  const [debugReplayOpen, setDebugReplayOpen] = useState(false);
  const [debugReplayTrace, setDebugReplayTrace] = useState<any>(null);

  useEffect(() => {
    if (visible) {
      setName(initialValues.name || '');
      setCron(initialValues.cron || '');
      setDirectoryId(initialValues.directoryId);
      setEnabled(initialValues.enabled !== false);
      setLogEnabled(!!initialValues.logEnabled);
      setLogMode(
        initialValues.logMode || (initialValues.logEnabled === false ? 'OFF' : (initialValues.logEnabled === true ? 'ALL' : 'SYSTEM_DEFAULT')),
      );
      setLogRetentionDays(initialValues.logRetentionDays ?? undefined);
      setInfo(initialValues.info || '');
      setDslContent(initialValues.dslContent || '');
      setPublishStatus(initialValues.publishStatus === 1 ? 1 : 0);
      setHasUnpublishedChanges(!!initialValues.hasUnpublishedChanges);
      setSubmitAttempted(false);
      setActiveTab(
        initialTab && (initialTab !== 'runtime' || !!initialValues.id)
          ? initialTab
          : 'basic',
      );
    }
  }, [visible, initialValues, initialTab]);

  const buildPayload = useCallback((): Partial<FlowTask> | null => {
    setSubmitAttempted(true);
    if (!name?.trim()) {
      message.warning('请输入任务名称');
      return null;
    }
    if (!cron?.trim()) {
      message.warning('请输入 Cron 表达式');
      return null;
    }
    return {
      name: name.trim(),
      cron: cron.trim(),
      enabled,
      logEnabled: logMode === 'ALL' || logMode === 'ERROR_ONLY',
      logMode,
      // 留空提交 -1：后端语义为清除任务级配置（回退系统保留天数）
      logRetentionDays: logRetentionDays ?? -1,
      info: info || undefined,
      dslContent: dslContent?.trim() || DEFAULT_SCHEDULE_DSL,
      directoryId: directoryId || '',
    };
  }, [name, cron, enabled, logEnabled, logMode, logRetentionDays, info, dslContent, directoryId]);

  const handleSave = useCallback(async () => {
    const payload = buildPayload();
    if (!payload) return;
    onSubmit(payload);
  }, [buildPayload, onSubmit]);

  const applyDetail = useCallback((detail: FlowTask) => {
    setPublishStatus(detail.publishStatus === 1 ? 1 : 0);
    setHasUnpublishedChanges(!!detail.hasUnpublishedChanges);
    if (detail.dslContent != null) setDslContent(detail.dslContent);
    if (detail.name != null) setName(detail.name);
    if (detail.cron != null) setCron(detail.cron);
    if (detail.directoryId !== undefined) setDirectoryId(detail.directoryId || undefined);
    if (detail.enabled != null) setEnabled(!!detail.enabled);
    if (detail.logEnabled != null) setLogEnabled(!!detail.logEnabled);
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
      await updateTask(initialValues.id, payload);
      if (isRepublish) {
        await republishTask(initialValues.id);
      } else {
        await publishTask(initialValues.id);
      }
      const detail = unwrapTask(await getTask(initialValues.id));
      hide();
      message.success(isRepublish ? '发布更新成功' : '发布成功');
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
      await unpublishTask(initialValues.id);
      const detail = unwrapTask(await getTask(initialValues.id));
      hide();
      message.success('下线成功，调度已停止');
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
      await rollbackTask(initialValues.id);
      const detail = unwrapTask(await getTask(initialValues.id));
      hide();
      message.success('已回滚到线上版本');
      applyDetail(detail);
    } catch (e: any) {
      hide();
      message.error(e?.message || '回滚失败');
    }
  }, [initialValues.id, applyDetail]);

  const handleDebugRun = useCallback(async () => {
    if (!dslContent) {
      message.warning('请先配置流程');
      return;
    }
    const hide = message.loading('正在调试运行...');
    try {
      const result = await debugRunTask(dslContent, {
        sourceRef: initialValues.id,
        sourceName: name || initialValues.name,
        cron: cron || undefined,
      });
      const trace = unwrapDebugTrace(result);
      hide();
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
  }, [dslContent, initialValues.id, initialValues.name, name, cron]);

  const headerTitle = (
    <Space>
      <span style={{ fontWeight: 600, fontSize: 15 }}>
        {isEdit ? `编辑任务：${name}` : '新建任务'}
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
      <Tooltip title="调试：立即运行一次当前草稿流程（不依赖发布状态），返回 FlowTrace">
        <Button icon={<PlayCircleOutlined />} onClick={handleDebugRun}>
          调试运行
        </Button>
      </Tooltip>

      {isEdit && (
        <HistoryVersionButton
          disabled={!initialValues.id}
          onClick={() => setHistoryOpen(true)}
        />
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
    <div className={ASSET_FORM_SCROLL_CLASS}>
      <div className={ASSET_FORM_BASIC_CLASS}>
      <Form layout="vertical" form={form}>
        <Row gutter={[24, 0]} className="yf-asset-form-row">
          <Col span={24}>
            <div className="yf-asset-form-section-title">基本配置</div>
          </Col>
          <Col {...ASSET_FORM_COL_FIELD}>
            <Form.Item
              label="任务名称"
              required
              validateStatus={submitAttempted && !name?.trim() ? 'error' : ''}
              help={submitAttempted && !name?.trim() ? '请输入任务名称' : undefined}
            >
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="请输入任务名称"
              />
            </Form.Item>
          </Col>
          <Col {...ASSET_FORM_COL_FIELD}>
            <DirectoryTreeSelect
              bizType="task"
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
          </Col>
          <Col {...ASSET_FORM_COL_FIELD}>
            <Form.Item
              label="Cron 表达式"
              required
              validateStatus={submitAttempted && !cron?.trim() ? 'error' : ''}
              help={
                submitAttempted && !cron?.trim()
                  ? '请输入 Cron 表达式'
                  : (
                    <span style={{ fontSize: 12, color: '#8c8c8c' }}>
                      Spring 6 字段：秒 分 时 日 月 周，如 <code>0 0/5 * * * ?</code>
                    </span>
                  )
              }
            >
              <Input
                value={cron}
                onChange={(e) => setCron(e.target.value)}
                placeholder="例如：0 0 2 * * ?"
                style={{ fontFamily: 'monospace' }}
              />
            </Form.Item>
          </Col>
          <Col {...ASSET_FORM_COL_FIELD}>
            <Form.Item
              label="启用状态"
              help={
                <span style={{ fontSize: 12, color: '#8c8c8c' }}>
                  启用且已发布后才会注册调度；调试运行始终使用当前草稿。
                </span>
              }
            >
              <Switch
                checked={enabled}
                onChange={setEnabled}
                checkedChildren="启用"
                unCheckedChildren="停用"
              />
            </Form.Item>
          </Col>
          <Col span={24}>
            <div className="yf-asset-form-section-title">日志</div>
          </Col>
          <Col {...ASSET_FORM_COL_HALF}>
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
                <Tooltip title="显式指定当前任务仅在发生报错/失败时记录日志">
                  <Radio.Button value="ERROR_ONLY">仅错误</Radio.Button>
                </Tooltip>
                <Tooltip title="显式指定当前任务全量记录成功与失败日志（含 FlowTrace 快照）">
                  <Radio.Button value="ALL">全量记录</Radio.Button>
                </Tooltip>
                <Tooltip title="显式指定当前任务完全禁用日志记录，任何情况下均不落库">
                  <Radio.Button value="OFF">完全关闭</Radio.Button>
                </Tooltip>
              </Radio.Group>
              <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                {(!logMode || logMode === 'SYSTEM_DEFAULT') && `当前全局：【${getLogModeLabel(globalLogMode)}】`}
                {logMode === 'ERROR_ONLY' && '覆盖全局：仅错误'}
                {logMode === 'ALL' && '覆盖全局：全量记录'}
                {logMode === 'OFF' && '覆盖全局：完全关闭'}
              </div>
            </Form.Item>
          </Col>
          <Col {...ASSET_FORM_COL_HALF}>
            <Form.Item
              label="日志保留天数"
              help={
                <span style={{ fontSize: 12, color: '#8c8c8c' }}>
                  留空=跟随系统配置，0=永久保留，&gt;0=按天数自动清理
                </span>
              }
            >
              <InputNumber
                value={logRetentionDays}
                onChange={(v) => setLogRetentionDays(v ?? undefined)}
                min={0}
                precision={0}
                style={{ width: '100%' }}
                placeholder="留空跟随系统配置"
              />
            </Form.Item>
          </Col>
          <Col {...ASSET_FORM_COL_FULL}>
            <Form.Item label="任务描述">
              <Input.TextArea
                value={info}
                onChange={(e) => setInfo(e.target.value)}
                rows={3}
                placeholder="可选：任务的功能说明"
              />
            </Form.Item>
          </Col>
        </Row>
      </Form>
      </div>
    </div>
  );

  const renderTabContent = () => {
    if (activeTab === 'basic') {
      return basicInfoContent;
    }
    if (activeTab === 'runtime') {
      return (
        <div className={ASSET_FORM_SCROLL_CLASS}>
          <AssetRuntimePanel assetType="TASK" assetId={initialValues.id} />
        </div>
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
          defaultEntryNode="schedule"
          editorContext="task"
          apiId={initialValues.id}
          apiName={name}
          debugAdapters={{
            onRun: async (payload) => {
              const result = await debugRunTask(payload.dslContent, {
                sourceRef: initialValues.id,
                sourceName: name,
                cron: cron || undefined,
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
          ...(initialValues.id ? [{ tab: '运行', key: 'runtime' }] : []),
        ]}
        style={{ height: '100%', overflow: 'hidden' }}
      >
        {renderTabContent()}
      </PageContainer>

      {initialValues.id && (
        <AssetVersionHistoryDrawer
          open={historyOpen}
          onClose={() => setHistoryOpen(false)}
          title={name || initialValues.name}
          loadVersions={async () => {
            const res = await listTaskVersions(initialValues.id!);
            return (Array.isArray(res) ? res : (res as any)?.data) || [];
          }}
          restoreVersion={async (versionId) => {
            await restoreTaskVersion(initialValues.id!, versionId);
          }}
          onRestored={async () => {
            const detail = unwrapTask(await getTask(initialValues.id!));
            applyDetail(detail);
          }}
        />
      )}

      {debugReplayOpen && (
        <Drawer
          title={`调试 Trace - ${name || initialValues.name || '任务'}`}
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
              defaultEntryNode="schedule"
              editorContext="task"
            />
          ) : null}
        </Drawer>
      )}
    </AssetFormShell>
  );
};

export default TaskForm;
