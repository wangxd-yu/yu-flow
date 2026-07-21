/**
 * TaskForm.tsx
 * 任务管理 · 核心配置页面
 * - 草稿 + 发布快照；调度仅跑已发布版本
 * - 支持历史版本回退（同步草稿与线上快照）
 */
import React, { useState, useEffect, useCallback } from 'react';
import {
  Drawer, message, Button, Form, Input, Switch, Space, Tooltip, Tag,
} from 'antd';
import {
  SaveOutlined, CloseOutlined, PlayCircleOutlined,
  CloudUploadOutlined, CloudDownloadOutlined, RollbackOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import type { FlowTask } from '../services/taskService';
import FlowEditor from '../../controller/components/FlowEditor';
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
} from '../services/taskService';
import AssetVersionHistoryDrawer, { HistoryVersionButton } from '../../components/AssetVersionHistoryDrawer';

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

export interface TaskFormProps {
  visible: boolean;
  isEdit: boolean;
  initialValues?: Partial<FlowTask>;
  onCancel: () => void;
  onSubmit: (values: Partial<FlowTask>) => void;
}

const TaskForm: React.FC<TaskFormProps> = ({
  visible, isEdit, initialValues = {}, onCancel, onSubmit,
}) => {
  const [form] = Form.useForm();

  const [name, setName] = useState<string>(initialValues.name || '');
  const [cron, setCron] = useState<string>(initialValues.cron || '');
  const [enabled, setEnabled] = useState<boolean>(initialValues.enabled !== false);
  const [logEnabled, setLogEnabled] = useState<boolean>(initialValues.logEnabled !== false);
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

  useEffect(() => {
    if (visible) {
      setName(initialValues.name || '');
      setCron(initialValues.cron || '');
      setEnabled(initialValues.enabled !== false);
      setLogEnabled(initialValues.logEnabled !== false);
      setInfo(initialValues.info || '');
      setDslContent(initialValues.dslContent || '');
      setPublishStatus(initialValues.publishStatus === 1 ? 1 : 0);
      setHasUnpublishedChanges(!!initialValues.hasUnpublishedChanges);
      setSubmitAttempted(false);
      setActiveTab('basic');
    }
  }, [visible, initialValues]);

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
      logEnabled,
      info: info || undefined,
      dslContent: dslContent?.trim() || DEFAULT_SCHEDULE_DSL,
      directoryId: initialValues.directoryId,
    };
  }, [name, cron, enabled, logEnabled, info, dslContent, initialValues.directoryId]);

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
    if (detail.enabled != null) setEnabled(!!detail.enabled);
    if (detail.logEnabled != null) setLogEnabled(!!detail.logEnabled);
    if (detail.info != null) setInfo(detail.info);
  }, []);

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
      const trace = await debugRunTask(dslContent, {
        sourceRef: initialValues.id,
        sourceName: name || initialValues.name,
      });
      hide();
      if (trace?.status === 'error') {
        message.error(`执行失败: ${trace.errorMsg}`);
      } else {
        message.success('调试运行成功');
      }
    } catch (e: any) {
      hide();
      message.error('调试失败: ' + (e?.message || '未知错误'));
    }
  }, [dslContent, initialValues.id, initialValues.name, name]);

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
      <Tooltip title="调试：立即运行一次当前草稿流程（不依赖发布状态）">
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
            placeholder="请输入任务名称"
          />
        </Form.Item>

        <Form.Item
          label="Cron 表达式"
          required
          validateStatus={submitAttempted && !cron?.trim() ? 'error' : ''}
          help={
            submitAttempted && !cron?.trim()
              ? '请输入 Cron 表达式'
              : (
                <span style={{ fontSize: 12, color: '#8c8c8c' }}>
                  格式：秒 分 时 日 月 周，例如：<code>0 0/5 * * * ?</code>（每5分钟执行一次）
                </span>
              )
          }
        >
          <Input
            value={cron}
            onChange={(e) => setCron(e.target.value)}
            placeholder="例如：0 0 2 * * ?（每天凌晨2点）"
            style={{ fontFamily: 'monospace' }}
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
            启用且已发布后才会注册调度；调试运行始终使用当前草稿。
          </div>
        </Form.Item>

        <Form.Item label="开启日志">
          <Switch
            checked={logEnabled}
            onChange={setLogEnabled}
            checkedChildren="开启"
            unCheckedChildren="关闭"
          />
          <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
            开启后，每次执行的 FlowTrace 快照将被记录到任务日志中
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
    return (
      <div className="task-form-fill">
        <FlowEditor
          value={dslContent}
          onChange={setDslContent}
          onSave={handleSave}
          onCancel={onCancel}
          isEdit={isEdit}
          height="100%"
          defaultEntryNode="schedule"
          editorContext="task"
          apiId={initialValues.id}
          apiName={name}
          debugAdapters={{
            onRun: async (payload) => {
              const result = await debugRunTask(payload.dslContent, {
                sourceRef: initialValues.id,
                sourceName: name,
              });
              if (result?.code === 0 && result.data) return result.data;
              if (result?.data) return result.data;
              if (result?.traceId) return result;
              throw new Error(result?.msg || '调试运行失败');
            },
          }}
        />
      </div>
    );
  };

  return (
    <Drawer
      title={null}
      width="100%"
      open={visible}
      onClose={onCancel}
      closable={false}
      styles={{
        body: { padding: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column', height: '100%' },
      }}
      destroyOnClose
    >
      <style>{`
        .task-form-page-container.ant-pro-page-container {
          display: flex !important;
          flex-direction: column !important;
          height: 100% !important;
          overflow: hidden !important;
        }
        .task-form-page-container .ant-page-header { flex-shrink: 0; }
        .task-form-page-container > .ant-pro-grid-content,
        .task-form-page-container .ant-pro-grid-content-children {
          flex: 1 !important;
          min-height: 0 !important;
          display: flex !important;
          flex-direction: column !important;
          overflow: hidden !important;
        }
        .task-form-page-container .ant-page-header {
          padding-inline: 20px !important;
        }
        .task-form-page-container .ant-tabs-nav {
          padding-inline: 20px !important;
          margin: 0 !important;
        }
        .task-form-page-container .ant-pro-page-container-children-container {
          flex: 1 !important;
          min-height: 0 !important;
          display: flex !important;
          flex-direction: column !important;
          height: auto !important;
          padding: 8px 20px 12px !important;
          overflow: hidden !important;
          box-sizing: border-box !important;
        }
        .task-form-fill {
          flex: 1 !important;
          min-height: 0 !important;
          display: flex !important;
          flex-direction: column !important;
          overflow: hidden !important;
        }
      `}</style>
      <PageContainer
        className="task-form-page-container"
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
    </Drawer>
  );
};

export default TaskForm;
