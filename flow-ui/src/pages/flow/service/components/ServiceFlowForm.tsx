/**
 * 服务管理 · 配置页
 * - 基本信息 / 服务契约 / 流程编排
 * - 契约在独立 Tab 编辑；Service 卡片同步展示入参摘要
 */
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Drawer, message, Button, Form, Input, Switch, Space, Tooltip, Typography, Tag,
} from 'antd';
import {
  SaveOutlined, CloseOutlined, PlayCircleOutlined, ThunderboltOutlined,
  CloudUploadOutlined, CloudDownloadOutlined, RollbackOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import type { FlowServiceFlow } from '@/services/flow/serviceFlowService';
import FlowEditor from '@/components/flow/FlowEditorLazy';
import {
  debugRunServiceFlow,
  updateServiceFlow,
  publishServiceFlow,
  unpublishServiceFlow,
  republishServiceFlow,
  rollbackServiceFlow,
  getServiceFlow,
  listServiceFlowVersions,
  restoreServiceFlowVersion,
} from '@/services/flow/serviceFlowService';
import AssetVersionHistoryDrawer, { HistoryVersionButton } from '@/components/flow/AssetVersionHistoryDrawer';
import AssetRuntimePanel from '@/components/flow/AssetRuntimePanel';
import {
  AssetFormShell,
  ASSET_FORM_SHELL_CLASS,
  ASSET_FORM_FILL_CLASS,
} from '@/components/flow/ops';
import { SchemaTreeTable } from '@/components/flow/ApiContractDesigner';
import type { SchemaNode } from '@/components/flow/ApiContractDesigner';
import {
  parseServiceContract,
  stringifyServiceContract,
  injectContractIntoServiceDsl,
  buildSampleInputFromContract,
  type ServiceContract,
} from '@/services/flow/serviceContract';
import ServiceManualRunModal from './ServiceManualRunModal';
import { confirmServiceUnpublish } from './confirmServiceUnpublish';
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect';

function unwrapDebugTrace(result: any) {
  if (result?.code === 0 && result.data) return result.data;
  if (result?.data) return result.data;
  if (result?.traceId || result?.stepLogs) return result;
  throw new Error(result?.msg || '调试运行失败');
}

const { Text } = Typography;

const DEFAULT_SERVICE_DSL = JSON.stringify({
  nodes: [
    {
      id: 'service_1',
      type: 'service',
      label: 'Service',
      x: 80,
      y: 160,
      width: 240,
      height: 104,
      ports: [{ id: 'out' }],
      data: {
        __label: 'Service',
      },
    },
  ],
  edges: [],
});

function unwrapService(res: any): FlowServiceFlow {
  return (res?.data || res) as FlowServiceFlow;
}

export interface ServiceFlowFormProps {
  visible: boolean;
  isEdit: boolean;
  initialValues?: Partial<FlowServiceFlow>;
  onCancel: () => void;
  onSubmit: (values: Partial<FlowServiceFlow>) => void;
  onPublished?: (detail: FlowServiceFlow) => void;
  /** 打开时默认 Tab（如运行中心深链） */
  initialTab?: string;
}

const ServiceFlowForm: React.FC<ServiceFlowFormProps> = ({
  visible, isEdit, initialValues = {}, onCancel, onSubmit, onPublished, initialTab,
}) => {
  const [form] = Form.useForm();
  const [name, setName] = useState<string>(initialValues.name || '');
  const [directoryId, setDirectoryId] = useState<string | undefined>(initialValues.directoryId);
  const [enabled, setEnabled] = useState<boolean>(initialValues.enabled !== false);
  const [logEnabled, setLogEnabled] = useState<boolean>(!!initialValues.logEnabled);
  const [info, setInfo] = useState<string>(initialValues.info || '');
  const [dslContent, setDslContent] = useState<string>(initialValues.dslContent || '');
  const [contract, setContract] = useState<ServiceContract>(() =>
    parseServiceContract(initialValues.contract),
  );
  const [publishStatus, setPublishStatus] = useState<0 | 1>(
    (initialValues.publishStatus === 1 ? 1 : 0),
  );
  const [hasUnpublishedChanges, setHasUnpublishedChanges] = useState<boolean>(
    !!initialValues.hasUnpublishedChanges,
  );
  const [activeTab, setActiveTab] = useState<string>('basic');
  const [submitAttempted, setSubmitAttempted] = useState<boolean>(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [debugReplayOpen, setDebugReplayOpen] = useState(false);
  const [debugReplayTrace, setDebugReplayTrace] = useState<any>(null);
  const [manualRunOpen, setManualRunOpen] = useState(false);

  const sampleInputJson = useMemo(
    () => buildSampleInputFromContract(contract),
    [contract],
  );
  const contractJson = useMemo(
    () => stringifyServiceContract(contract),
    [contract],
  );

  useEffect(() => {
    if (visible) {
      const nextContract = parseServiceContract(initialValues.contract);
      setName(initialValues.name || '');
      setDirectoryId(initialValues.directoryId);
      setEnabled(initialValues.enabled !== false);
      setLogEnabled(!!initialValues.logEnabled);
      setInfo(initialValues.info || '');
      setContract(nextContract);
      setDslContent(
        injectContractIntoServiceDsl(
          initialValues.dslContent?.trim() || DEFAULT_SERVICE_DSL,
          nextContract,
        ),
      );
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

  /** 改契约时同步写入 DSL，保证切到编排页卡片立刻显示入参摘要 */
  const updateContract = useCallback((next: ServiceContract) => {
    setContract(next);
    setDslContent((prev) =>
      injectContractIntoServiceDsl(prev?.trim() || DEFAULT_SERVICE_DSL, next),
    );
  }, []);

  const buildPayload = useCallback((): Partial<FlowServiceFlow> | null => {
    setSubmitAttempted(true);
    if (!name?.trim()) {
      message.warning('请输入服务名称');
      return null;
    }
    const baseDsl = dslContent?.trim() || DEFAULT_SERVICE_DSL;
    return {
      name: name.trim(),
      enabled,
      logEnabled,
      info: info || undefined,
      dslContent: injectContractIntoServiceDsl(baseDsl, contract),
      contract: stringifyServiceContract(contract),
      directoryId: directoryId || '',
    };
  }, [name, enabled, logEnabled, info, dslContent, contract, directoryId]);

  const handleSave = useCallback(async () => {
    const payload = buildPayload();
    if (!payload) return;
    onSubmit(payload);
  }, [buildPayload, onSubmit]);

  const applyDetail = useCallback((detail: FlowServiceFlow) => {
    setPublishStatus(detail.publishStatus === 1 ? 1 : 0);
    setHasUnpublishedChanges(!!detail.hasUnpublishedChanges);
    const nextContract = parseServiceContract(detail.contract);
    setContract(nextContract);
    if (detail.dslContent != null) {
      setDslContent(injectContractIntoServiceDsl(detail.dslContent, nextContract));
    }
    if (detail.name != null) setName(detail.name);
    if (detail.directoryId !== undefined) setDirectoryId(detail.directoryId || undefined);
    if (detail.enabled != null) setEnabled(!!detail.enabled);
    if (detail.logEnabled != null) setLogEnabled(!!detail.logEnabled);
    if (detail.info != null) setInfo(detail.info);
    onPublished?.(detail);
  }, [onPublished]);

  const handlePublish = useCallback(async () => {
    if (!isEdit || !initialValues.id) {
      message.warning('请先保存服务后再发布');
      return;
    }
    const payload = buildPayload();
    if (!payload) return;

    const isRepublish = publishStatus === 1;
    const hide = message.loading(isRepublish ? '正在发布更新...' : '正在发布...');
    try {
      await updateServiceFlow(initialValues.id, payload);
      if (isRepublish) {
        await republishServiceFlow(initialValues.id);
      } else {
        await publishServiceFlow(initialValues.id);
      }
      const detail = unwrapService(await getServiceFlow(initialValues.id));
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
    const ok = await confirmServiceUnpublish(initialValues.id, name || initialValues.name);
    if (!ok) return;
    const hide = message.loading('正在下线...');
    try {
      await unpublishServiceFlow(initialValues.id);
      const detail = unwrapService(await getServiceFlow(initialValues.id));
      hide();
      message.success('下线成功');
      applyDetail(detail);
    } catch (e: any) {
      hide();
      message.error(e?.message || '下线失败');
    }
  }, [initialValues.id, initialValues.name, name, applyDetail]);

  const handleRollback = useCallback(async () => {
    if (!initialValues.id) return;
    const hide = message.loading('正在回滚...');
    try {
      await rollbackServiceFlow(initialValues.id);
      const detail = unwrapService(await getServiceFlow(initialValues.id));
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
      const result = await debugRunServiceFlow(
        dslContent,
        {
          sourceRef: initialValues.id,
          sourceName: name || initialValues.name,
        },
        sampleInputJson,
        contractJson,
      );
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
  }, [dslContent, initialValues.id, initialValues.name, name, sampleInputJson, contractJson]);

  const headerTitle = (
    <Space>
      <span style={{ fontWeight: 600, fontSize: 15 }}>
        {isEdit ? `编辑服务：${name}` : '新建服务'}
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

      {isEdit && initialValues.id && (
        <Tooltip title="手动调用：执行服务端已保存草稿，返回业务输出（非 Trace）">
          <Button
            icon={<ThunderboltOutlined />}
            onClick={() => setManualRunOpen(true)}
          >
            手动调用
          </Button>
        </Tooltip>
      )}

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
          label="服务名称"
          required
          validateStatus={submitAttempted && !name?.trim() ? 'error' : ''}
          help={submitAttempted && !name?.trim() ? '请输入服务名称' : undefined}
        >
          <Input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="请输入服务名称，如：生成 Token、通用查询"
          />
        </Form.Item>

        <DirectoryTreeSelect
          bizType="service"
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

        <Form.Item label="启用状态">
          <Switch
            checked={enabled}
            onChange={setEnabled}
            checkedChildren="启用"
            unCheckedChildren="停用"
          />
          <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
            停用后，其他流程通过 api 节点调用将失败。调用方仅能选择「已发布」的服务。
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
            开启后，每次执行的 FlowTrace 快照将被记录到服务日志中
          </div>
        </Form.Item>

        <Form.Item label="服务描述">
          <Input.TextArea
            value={info}
            onChange={(e) => setInfo(e.target.value)}
            rows={3}
            placeholder="可选：服务的功能说明"
          />
        </Form.Item>
      </Form>
    </div>
  );

  const contractContent = (
    <div style={{ padding: '12px 0 24px', maxWidth: 960 }}>
      <Text type="secondary" style={{ display: 'block', marginBottom: 12, fontSize: 12 }}>
        定义调用方可见的入参与返回结构。修改后会同步到流程编排中的 Service 卡片摘要；
        发布后，其他流程的 api 节点按已发布契约同步入参。
      </Text>

      <div style={{ marginBottom: 28 }}>
        <div style={{ fontWeight: 600, marginBottom: 8 }}>入口参数</div>
        <SchemaTreeTable
          value={contract.inputs}
          onChange={(inputs: SchemaNode[]) => updateContract({ ...contract, inputs })}
        />
      </div>

      <div style={{ marginBottom: 16 }}>
        <div style={{ fontWeight: 600, marginBottom: 8 }}>返回值结构</div>
        <SchemaTreeTable
          value={contract.outputs}
          onChange={(outputs: SchemaNode[]) => updateContract({ ...contract, outputs })}
        />
        <Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
          画布上仅有一个出口 <code>out</code>，返回值可以是对象（不必为每个字段单独开端口）。
        </Text>
      </div>

      <Form layout="vertical" style={{ maxWidth: 640 }}>
        <Form.Item label="返回值说明" style={{ marginBottom: 0 }}>
          <Input.TextArea
            value={contract.outputDescription || ''}
            onChange={(e) =>
              updateContract({ ...contract, outputDescription: e.target.value })
            }
            rows={3}
            placeholder="例如：返回 { token, expireAt }，token 为 JWT 字符串"
          />
        </Form.Item>
      </Form>
    </div>
  );

  const renderTabContent = () => {
    if (activeTab === 'basic') return basicInfoContent;
    if (activeTab === 'contract') return contractContent;
    if (activeTab === 'runtime') {
      return (
        <div style={{ overflow: 'auto', height: '100%', padding: '0 8px' }}>
          <AssetRuntimePanel assetType="SERVICE" assetId={initialValues.id} />
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
          defaultEntryNode="service"
          editorContext="service"
          triggerMode="service"
          defaultTriggerBody={sampleInputJson}
          apiId={initialValues.id}
          apiName={name}
          debugAdapters={{
            onRun: async (payload) => {
              return debugRunServiceFlow(
                payload.dslContent,
                { sourceRef: initialValues.id, sourceName: name },
                payload.body,
                contractJson,
              );
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
          { tab: '服务契约', key: 'contract' },
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
            const res = await listServiceFlowVersions(initialValues.id!);
            return (Array.isArray(res) ? res : (res as any)?.data) || [];
          }}
          restoreVersion={async (versionId) => {
            await restoreServiceFlowVersion(initialValues.id!, versionId);
          }}
          onRestored={async () => {
            const detail = unwrapService(await getServiceFlow(initialValues.id!));
            applyDetail(detail);
          }}
        />
      )}

      {debugReplayOpen && (
        <Drawer
          title={`调试 Trace - ${name || initialValues.name || '服务'}`}
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
              defaultEntryNode="service"
              editorContext="service"
              triggerMode="service"
            />
          ) : null}
        </Drawer>
      )}

      <ServiceManualRunModal
        open={manualRunOpen}
        serviceId={initialValues.id}
        serviceName={name || initialValues.name}
        onClose={() => setManualRunOpen(false)}
      />
    </AssetFormShell>
  );
};

export default ServiceFlowForm;
