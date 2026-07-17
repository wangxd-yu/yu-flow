/**
 * TaskForm.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * 任务管理 · 核心配置页面
 *
 * 相比 ControllerForm 的简化点：
 *   1. 无 HTTP Method / URL 配置（任务通过 Cron 触发）
 *   2. 无发布快照机制（保存即生效）
 *   3. 仅提供 FLOW 编排模式（无 DB / JSON / STRING）
 *   4. 新建时默认插入 schedule 节点作为流程起点
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React, { useState, useEffect, useCallback } from 'react';
import {
  Drawer, message, Button, Form, Input, Switch, Space, Tooltip,
} from 'antd';
import { SaveOutlined, CloseOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import type { FlowTask } from '../services/taskService';
import FlowEditor from '../../controller/components/FlowEditor';
import { debugRunTask } from '../services/taskService';

// ── 默认 schedule 节点 DSL（新建时插入） ──
// 与 Request 默认占位一致：靠左 x=80（画布空白时由 FlowEditor 按此逻辑创建）
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

// ── Props ──

export interface TaskFormProps {
  visible: boolean;
  isEdit: boolean;
  initialValues?: Partial<FlowTask>;
  onCancel: () => void;
  onSubmit: (values: Partial<FlowTask>) => void;
}

// ── 主组件 ──

const TaskForm: React.FC<TaskFormProps> = ({
  visible, isEdit, initialValues = {}, onCancel, onSubmit,
}) => {
  const [form] = Form.useForm();

  // 基本信息状态
  const [name, setName] = useState<string>(initialValues.name || '');
  const [cron, setCron] = useState<string>(initialValues.cron || '');
  const [enabled, setEnabled] = useState<boolean>(initialValues.enabled !== false);
  const [logEnabled, setLogEnabled] = useState<boolean>(initialValues.logEnabled !== false);
  const [info, setInfo] = useState<string>(initialValues.info || '');

  // 流程编排状态：新建时留空，由 FlowEditor 在左侧生成 schedule（与 Request 一致）
  const [dslContent, setDslContent] = useState<string>(initialValues.dslContent || '');

  const [activeTab, setActiveTab] = useState<string>('basic');
  const [submitAttempted, setSubmitAttempted] = useState<boolean>(false);

  // 初始化
  useEffect(() => {
    if (visible) {
      setName(initialValues.name || '');
      setCron(initialValues.cron || '');
      setEnabled(initialValues.enabled !== false);
      setLogEnabled(initialValues.logEnabled !== false);
      setInfo(initialValues.info || '');
      // 新建不预填 DSL，避免 centerContent 把节点居中；编辑加载已有 DSL
      setDslContent(initialValues.dslContent || '');
      setSubmitAttempted(false);
      setActiveTab('basic');
    }
  }, [visible, initialValues]);

  // 保存
  const handleSave = useCallback(async () => {
    setSubmitAttempted(true);
    if (!name?.trim()) {
      message.warning('请输入任务名称');
      return;
    }
    if (!cron?.trim()) {
      message.warning('请输入 Cron 表达式');
      return;
    }

    onSubmit({
      name: name.trim(),
      cron: cron.trim(),
      enabled,
      logEnabled,
      info: info || undefined,
      // 未进入流程编排时，写入默认左侧 schedule DSL
      dslContent: dslContent?.trim() || DEFAULT_SCHEDULE_DSL,
      directoryId: initialValues.directoryId,
    });
  }, [name, cron, enabled, logEnabled, info, dslContent, initialValues.directoryId, onSubmit]);

  // 调试运行
  const handleDebugRun = useCallback(async () => {
    if (!dslContent) {
      message.warning('请先配置流程');
      return;
    }
    const hide = message.loading('正在调试运行...');
    try {
      const trace = await debugRunTask(dslContent);
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
  }, [dslContent]);

  // ── Header ──
  const headerTitle = (
    <Space>
      <span style={{ fontWeight: 600, fontSize: 15 }}>
        {isEdit ? `编辑任务：${name}` : '新建任务'}
      </span>
    </Space>
  );

  const headerExtra = (
    <Space size={8}>
      <Tooltip title="调试：立即运行一次当前流程">
        <Button icon={<PlayCircleOutlined />} onClick={handleDebugRun}>
          调试运行
        </Button>
      </Tooltip>
      <Button icon={<CloseOutlined />} onClick={onCancel}>取消</Button>
      <Button type="primary" icon={<SaveOutlined />} onClick={handleSave}>
        保存
      </Button>
    </Space>
  );

  // ── Tab 内容 ──

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

  // 与 ControllerForm 一致：按 Tab 条件挂载，避免 FlowEditor 在 display:none
  // 容器（宽高为 0）中初始化 X6，触发 SVGMatrix non-finite 报错。
  const renderTabContent = () => {
    if (activeTab === 'basic') {
      return basicInfoContent;
    }
    return (
      <div style={{ height: 'calc(100vh - 160px)', minHeight: 500 }}>
        <FlowEditor
          value={dslContent}
          onChange={setDslContent}
          onSave={handleSave}
          onCancel={onCancel}
          isEdit={isEdit}
          height="100%"
          defaultEntryNode="schedule"
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
        body: { padding: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' },
      }}
      destroyOnClose
    >
      <style>{`
        .task-form-page-container .ant-pro-page-container-children-content {
          padding-bottom: 0 !important;
          margin-bottom: 0 !important;
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
        style={{ height: '100%', overflow: 'auto' }}
      >
        {renderTabContent()}
      </PageContainer>
    </Drawer>
  );
};

export default TaskForm;
